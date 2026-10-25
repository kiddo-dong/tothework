package com.tothework.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.identity.api.UserWithdrawnEvent;
import com.tothework.identity.api.WithdrawalGuard;
import com.tothework.identity.auth.EmailTokenRepository;
import com.tothework.identity.auth.RefreshTokenRepository;
import com.tothework.shared.error.ConflictException;
import com.tothework.shared.storage.S3Uploader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 내 정보 변경과 탈퇴. 변경 감지가 없으므로 값을 바꾼 뒤 저장했는지 확인하고, 탈퇴는 다른 서비스의 데이터를 직접
 * 지우지 않고 막을 조건은 {@link WithdrawalGuard}에, 정리는 {@link UserWithdrawnEvent}에 맡기는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private EmailTokenRepository emailTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private WithdrawalGuard withdrawalGuard;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private S3Uploader s3Uploader;
    @Mock private PlatformTransactionManager transactionManager;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, emailTokenRepository, refreshTokenRepository, List.of(withdrawalGuard),
                eventPublisher, s3Uploader, transactionManager);
        user = User.signUpLocally("me@albam.dev", "encoded", "테스트", "010-1111-2222", LocalDate.of(1990, 1, 1),
                LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.changeProfileImageKey("profile-images/old.png");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void withdrawalAnonymizesSavesAndAnnouncesIt() {
        userService.withdraw(USER_ID);

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("deleted-7@withdrawn.albam");
        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(new UserWithdrawnEvent(USER_ID));
        verify(emailTokenRepository).deleteAllOfUser(USER_ID);
        verify(refreshTokenRepository).deleteAllOfUser(USER_ID); // 남은 세션으로 재발급받지 못하게
        verify(s3Uploader).delete("profile-images/old.png");
    }

    /** 가드가 거부하면 아무것도 바꾸거나 알리지 않아야 한다. 이벤트가 나가면 다른 서비스가 데이터를 지워 버린다. */
    @Test
    void doesNothingWhenAGuardRejects() {
        doThrow(new ConflictException("소속된 매장이 있으면 탈퇴할 수 없습니다.")).when(withdrawalGuard).checkCanWithdraw(USER_ID);

        assertThatThrownBy(() -> userService.withdraw(USER_ID)).isInstanceOf(ConflictException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteAllOfUser(any());
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void updateMeSavesTheChangedProfile() {
        userService.updateMe(USER_ID, new UpdateUserRequest("새이름", "010-3333-4444", null));

        assertThat(user.getName()).isEqualTo("새이름");
        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1)); // 안 보낸 생년월일은 그대로
        verify(userRepository).save(user);
    }

    /** 사진을 바꾸면 새 key를 저장하고, 커밋 뒤에 예전 사진을 지운다. */
    @Test
    void replacingThePhotoSavesTheNewKeyAndDeletesTheOldFile() {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});
        when(s3Uploader.upload(image, "profile-images")).thenReturn("profile-images/new.png");

        userService.updateProfileImage(USER_ID, image);

        assertThat(user.getProfileImageKey()).isEqualTo("profile-images/new.png");
        verify(userRepository).save(user);
        verify(s3Uploader).delete("profile-images/old.png");
    }
}
