package com.tothework.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.identity.config.IdentityProperties;
import com.tothework.identity.mail.MailService;
import com.tothework.identity.oauth.OAuthProfilePhotoImporter;
import com.tothework.identity.user.AuthProvider;
import com.tothework.identity.user.User;
import com.tothework.identity.user.UserRepository;
import com.tothework.shared.error.BusinessException;
import com.tothework.shared.error.ErrorCode;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.security.JwtTokens;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 리프레시 토큰의 폐기·회전·재사용 탐지. 토큰이 서명만으로 검증되던 시절에는 로그아웃해도 유출된 토큰을 만료일까지
 * 쓸 수 있었다. 저장된 기록이 있어야만 재발급되는지, 쓴 토큰은 지워지는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTokenTest {

    private static final String PRESENTED_TOKEN = "presented.refresh.token";
    private static final String NEW_REFRESH_TOKEN = "new.refresh.token";
    private static final String NEW_ACCESS_TOKEN = "new.access.token";

    @Mock private UserRepository userRepository;
    @Mock private EmailTokenRepository emailTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokens jwtTokens;
    @Mock private MailService mailService;
    @Mock private OAuthProfilePhotoImporter oAuthProfilePhotoImporter;
    @Mock private PlatformTransactionManager transactionManager;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, emailTokenRepository, refreshTokenRepository,
                passwordEncoder, jwtTokens, List.of(), mailService, oAuthProfilePhotoImporter, transactionManager,
                new IdentityProperties("http://localhost:8080", "http://localhost:5173", false, 7));
        user = User.signUpWithProvider("test@albam.dev", "테스트", AuthProvider.GOOGLE, "provider-id");
        ReflectionTestUtils.setField(user, "id", 1L);
        // 기본은 삭제 경합에서 이긴 경우. 진 경우는 해당 테스트에서 따로 0을 준다.
        lenient().when(refreshTokenRepository.deleteClaimed(any())).thenReturn(1);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    private RefreshToken storedRecord() {
        RefreshToken stored = RefreshToken.record(user.getId(), "hash", LocalDateTime.now().plusDays(14));
        ReflectionTestUtils.setField(stored, "id", 10L);
        return stored;
    }

    private void givenTokenIsAValidRefreshToken() {
        when(jwtTokens.isValid(PRESENTED_TOKEN, JwtTokens.Type.REFRESH)).thenReturn(true);
    }

    private void givenNewTokensAreIssued() {
        when(jwtTokens.issue(user.getId(), user.getEmail(), JwtTokens.Type.ACCESS)).thenReturn(NEW_ACCESS_TOKEN);
        when(jwtTokens.issue(user.getId(), user.getEmail(), JwtTokens.Type.REFRESH)).thenReturn(NEW_REFRESH_TOKEN);
        when(jwtTokens.expiresAtOf(NEW_REFRESH_TOKEN)).thenReturn(LocalDateTime.now().plusDays(14));
    }

    @Test
    void refreshIssuesNewTokensWhenTheStoredRecordExists() {
        givenTokenIsAValidRefreshToken();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRecord()));

        IssuedTokens issued = authService.refresh(PRESENTED_TOKEN);

        assertThat(issued.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        assertThat(issued.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
    }

    /** 폐기의 핵심. 서명과 만료가 멀쩡해도 기록이 없으면 통과시키지 않는다. */
    @Test
    void refreshIsRejectedWhenTheTokenWasRevoked() {
        givenTokenIsAValidRefreshToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(jwtTokens.userIdOf(PRESENTED_TOKEN)).thenReturn(user.getId());

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN)).isInstanceOf(InvalidRequestException.class);
        verify(jwtTokens, never()).issue(any(), anyString(), any());
    }

    /** 소비된 토큰이 다시 들어오면 유출 신호로 보고 그 사용자의 세션을 전부 끊는다. */
    @Test
    void refreshRevokesEverySessionWhenAConsumedTokenComesBack() {
        givenTokenIsAValidRefreshToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(jwtTokens.userIdOf(PRESENTED_TOKEN)).thenReturn(user.getId());

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN)).isInstanceOf(InvalidRequestException.class);

        verify(refreshTokenRepository).deleteAllOfUser(user.getId());
    }

    /** 정상 재발급까지 세션을 끊어버리면 안 된다. */
    @Test
    void refreshLeavesOtherSessionsAloneOnASuccessfulRotation() {
        givenTokenIsAValidRefreshToken();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRecord()));

        authService.refresh(PRESENTED_TOKEN);

        verify(refreshTokenRepository, never()).deleteAllOfUser(any());
    }

    /** 회전: 쓴 토큰은 지워지므로 같은 토큰으로 두 번 재발급받을 수 없다. */
    @Test
    void refreshDeletesTheUsedToken() {
        givenTokenIsAValidRefreshToken();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRecord()));

        authService.refresh(PRESENTED_TOKEN);

        verify(refreshTokenRepository).deleteClaimed(10L);
    }

    /** 서명이 틀렸거나 액세스 토큰을 리프레시 자리에 넣으면 조회조차 하지 않는다. */
    @Test
    void refreshIsRejectedWhenItIsNotAValidRefreshToken() {
        when(jwtTokens.isValid(PRESENTED_TOKEN, JwtTokens.Type.REFRESH)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN)).isInstanceOf(InvalidRequestException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    /** 같은 토큰을 두 탭이 동시에 쓰면 삭제는 한쪽만 성공한다. 진 쪽은 거절하되 세션을 전부 끊지는 않는다. */
    @Test
    void refreshThatLosesTheRaceIsRefusedWithoutEndingOtherSessions() {
        givenTokenIsAValidRefreshToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRecord()));
        when(refreshTokenRepository.deleteClaimed(any())).thenReturn(0);

        assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOKEN_ALREADY_ROTATED));
        verify(refreshTokenRepository, never()).deleteAllOfUser(any());
        verify(jwtTokens, never()).issue(any(), anyString(), any());
    }

    @Test
    void logoutDeletesTheStoredToken() {
        RefreshToken stored = storedRecord();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout(PRESENTED_TOKEN);

        verify(refreshTokenRepository).delete(stored);
    }

    /** 이미 로그아웃한 뒤 다시 눌러도 실패하면 안 된다. */
    @Test
    void logoutSucceedsWhenTheTokenIsAlreadyGone() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authService.logout(PRESENTED_TOKEN);

        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void logoutIsIgnoredWhenNoCookieWasSent() {
        authService.logout(null);

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    /** 같은 토큰 원문은 항상 같은 해시로 조회돼야 조회가 성립한다. */
    @Test
    void theSameTokenAlwaysResolvesToTheSameStoredHash() {
        givenTokenIsAValidRefreshToken();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRecord()));

        authService.refresh(PRESENTED_TOKEN);
        authService.refresh(PRESENTED_TOKEN);

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenRepository, times(2)).findByTokenHash(hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1));
    }

    /** 원문을 그대로 저장하면 DB 유출 시 바로 쓸 수 있는 토큰이 함께 넘어간다. */
    @Test
    void theStoredValueIsAHashRatherThanTheTokenItself() {
        givenTokenIsAValidRefreshToken();
        givenNewTokensAreIssued();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRecord()));

        authService.refresh(PRESENTED_TOKEN);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(NEW_REFRESH_TOKEN).hasSize(64);
    }
}
