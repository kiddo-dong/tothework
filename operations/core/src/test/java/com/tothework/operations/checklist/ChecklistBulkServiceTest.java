package com.tothework.operations.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.store.api.StoreAccess;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 업종 프리셋을 한 번에 넣는 경로. 배열 순서가 화면 순서가 되어야 하고, 이미 항목이 있는 매장에 넣어도 순서가 섞이지
 * 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class ChecklistBulkServiceTest {

    @Mock private ChecklistItemRepository checklistItemRepository;
    @Mock private ChecklistCompletionRepository checklistCompletionRepository;
    @Mock private StoreAccess storeAccess;
    @Mock private com.tothework.operations.people.MemberProfiles memberProfiles;

    @InjectMocks private ChecklistService checklistService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        when(checklistItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenExistingMaxOrder(ChecklistType type, Integer max) {
        when(checklistItemRepository.findMaxDisplayOrder(STORE_ID, type.name())).thenReturn(max);
    }

    private ChecklistItemBulkRequest request(ChecklistItemRequest... items) {
        return new ChecklistItemBulkRequest(List.of(items));
    }

    private ChecklistItemRequest open(String content) {
        return new ChecklistItemRequest(ChecklistType.OPEN, content, null);
    }

    private ChecklistItemRequest close(String content) {
        return new ChecklistItemRequest(ChecklistType.CLOSE, content, null);
    }

    @Test
    void numbersItemsInTheOrderTheyWereSent() {
        givenExistingMaxOrder(ChecklistType.OPEN, null);

        List<ChecklistItemResponse> saved = checklistService.addItems(STORE_ID, USER_ID,
                request(open("포스기 시재 확인"), open("매장 청소"), open("재료 확인")));

        assertThat(saved).extracting(ChecklistItemResponse::content)
                .containsExactly("포스기 시재 확인", "매장 청소", "재료 확인");
        assertThat(saved).extracting(ChecklistItemResponse::displayOrder).containsExactly(0, 1, 2);
    }

    /** 구분이 다르면 순번도 따로 간다 — 조회가 (구분, 순번)으로 정렬하기 때문이다. */
    @Test
    void numbersEachTypeSeparately() {
        givenExistingMaxOrder(ChecklistType.OPEN, null);
        givenExistingMaxOrder(ChecklistType.CLOSE, null);

        List<ChecklistItemResponse> saved = checklistService.addItems(STORE_ID, USER_ID,
                request(open("오픈1"), close("마감1"), open("오픈2"), close("마감2")));

        assertThat(saved).extracting(ChecklistItemResponse::displayOrder).containsExactly(0, 0, 1, 1);
    }

    /** 0부터 다시 매기면 기존 항목과 순번이 겹쳐 순서가 뒤섞인다. */
    @Test
    void appendsAfterItemsTheStoreAlreadyHas() {
        givenExistingMaxOrder(ChecklistType.OPEN, 4);

        List<ChecklistItemResponse> saved = checklistService.addItems(STORE_ID, USER_ID,
                request(open("새 항목1"), open("새 항목2")));

        assertThat(saved).extracting(ChecklistItemResponse::displayOrder).containsExactly(5, 6);
    }

    /** 저장은 한 번에 보낸다 — 단건 반복이면 중간에 실패했을 때 일부만 남는다. */
    @Test
    void savesEverythingInOneCall() {
        givenExistingMaxOrder(ChecklistType.OPEN, null);

        checklistService.addItems(STORE_ID, USER_ID, request(open("a"), open("b")));

        verify(checklistItemRepository, times(1)).saveAll(any());
        verify(checklistItemRepository, never()).save(any(ChecklistItem.class));
    }

    /** 권한 확인이 저장보다 먼저다. */
    @Test
    void requiresManagerRights() {
        givenExistingMaxOrder(ChecklistType.OPEN, null);

        checklistService.addItems(STORE_ID, USER_ID, request(open("a")));

        verify(storeAccess).requireOwnerOrManager(STORE_ID, USER_ID);
    }
}
