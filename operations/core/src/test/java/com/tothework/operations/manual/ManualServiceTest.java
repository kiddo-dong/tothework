package com.tothework.operations.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.operations.people.MemberProfiles;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.storage.S3Uploader;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 매뉴얼 이미지 key의 소유권 검사. 다른 매장의 S3 파일을 지울 수 있던 구멍을 막은 규칙이라 그대로 옮겼다. */
@ExtendWith(MockitoExtension.class)
class ManualServiceTest {

    @Mock private ManualRepository manualRepository;
    @Mock private ManualSummaries manualSummaries;
    @Mock private StoreAccess storeAccess;
    @Mock private MemberProfiles memberProfiles;
    @Mock private S3Uploader s3Uploader;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private ManualService manualService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long MANUAL_ID = 100L;

    private static final String OWN_KEY = "manual-images/1/abc-photo.png";
    private static final String OTHER_STORE_KEY = "manual-images/2/victim-photo.png";

    @BeforeEach
    void setUp() {
        Membership manager = new Membership(10L, STORE_ID, USER_ID, MemberRole.MANAGER, MemberStatus.ACTIVE, null,
                LocalDateTime.now(), null);
        Manual manual = new Manual(MANUAL_ID, STORE_ID, 10L, "조리", "커피 내리는 법", "내용", 0, null, null);
        manual.update("조리", "커피 내리는 법", "내용", 0, List.of(OWN_KEY));

        lenient().when(storeAccess.requireOwnerOrManager(anyLong(), anyLong())).thenReturn(manager);
        lenient().when(manualRepository.findByIdAndStoreId(MANUAL_ID, STORE_ID)).thenReturn(Optional.of(manual));
        lenient().when(manualRepository.save(any(Manual.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(memberProfiles.of(any(Long.class), any())).thenReturn(Map.of());
        lenient().when(s3Uploader.toPublicUrl(any())).thenAnswer(i -> "https://bucket/" + i.getArgument(0));
        // TransactionTemplate이 콜백을 그대로 실행하도록 한다.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void createManual_rejectsImageKeyOwnedByAnotherStore() {
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of(OTHER_STORE_KEY));

        assertThatThrownBy(() -> manualService.createManual(STORE_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateManual_rejectsImageKeyOwnedByAnotherStore() {
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of(OTHER_STORE_KEY));

        assertThatThrownBy(() -> manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateManual_neverDeletesAnotherStoresImage() {
        // 남의 key를 넣었다 빼는 방식으로 그 파일을 지우려는 시도. 요청 단계에서 막혀 삭제까지 가지 않아야 한다.
        ManualRequest attack = new ManualRequest("조리", "제목", "내용", 0, List.of(OTHER_STORE_KEY));

        assertThatThrownBy(() -> manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, attack))
                .isInstanceOf(ForbiddenException.class);

        verify(s3Uploader, never()).delete(any());
    }

    @Test
    void updateManual_deletesOnlyKeysDroppedFromThisManual() {
        manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID, new ManualRequest("조리", "제목", "내용", 0, List.of()));

        verify(s3Uploader).delete(OWN_KEY);
    }

    @Test
    void updateManual_keepsImageWhenStillReferenced() {
        manualService.updateManual(STORE_ID, MANUAL_ID, USER_ID,
                new ManualRequest("조리", "제목", "내용", 0, List.of(OWN_KEY)));

        verify(s3Uploader, never()).delete(any());
    }

    @Test
    void createManual_acceptsNullImageKeys() {
        assertThat(manualService.createManual(STORE_ID, USER_ID, new ManualRequest("조리", "제목", "내용", 0, null)))
                .isNotNull();
    }

    @Test
    void uploadImage_putsFileUnderThisStoresPath() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        when(s3Uploader.upload(any(), any())).thenReturn(OWN_KEY);

        manualService.uploadImage(STORE_ID, USER_ID, file);

        // 저장 시 소유권 판별이 경로 접두사에 의존하므로, 업로드도 반드시 매장별 경로여야 한다.
        verify(s3Uploader).upload(file, "manual-images/" + STORE_ID);
    }

    @Test
    void uploadImage_returnsBothKeyAndUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        when(s3Uploader.upload(any(), any())).thenReturn(OWN_KEY);

        ManualImageResponse uploaded = manualService.uploadImage(STORE_ID, USER_ID, file);

        // key는 저장 요청에 되돌려주기 위해, url은 방금 올린 이미지를 미리 보여주기 위해 둘 다 필요하다.
        assertThat(uploaded.key()).isEqualTo(OWN_KEY);
        assertThat(uploaded.url()).isEqualTo("https://bucket/" + OWN_KEY);
    }

    @Test
    void manualResponse_carriesKeyAndUrlForEachImage() {
        ManualResponse response = manualService.getManual(STORE_ID, MANUAL_ID, USER_ID);

        assertThat(response.images()).singleElement().satisfies(image -> {
            assertThat(image.key()).isEqualTo(OWN_KEY);
            assertThat(image.url()).isEqualTo("https://bucket/" + OWN_KEY);
        });
    }

    @Test
    void imageKeyOfOneStoreIsNotAPrefixMatchForAnother() {
        // manual-images/1/ 과 manual-images/12/ 를 혼동하지 않는지 (접두사 검사의 흔한 함정).
        ManualRequest request = new ManualRequest("조리", "제목", "내용", 0, List.of("manual-images/12/other.png"));

        assertThatThrownBy(() -> manualService.createManual(STORE_ID, USER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }
}
