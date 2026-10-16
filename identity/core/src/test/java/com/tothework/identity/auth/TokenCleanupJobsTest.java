package com.tothework.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.identity.config.IdentityProperties;
import com.tothework.identity.user.AuthProvider;
import com.tothework.identity.user.User;
import com.tothework.identity.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 방치된 계정 정리. 잘못 지우면 멀쩡한 사용자의 계정이 사라지므로 대상 조건과 삭제 순서를 확인한다. */
@ExtendWith(MockitoExtension.class)
class TokenCleanupJobsTest {

    private static final int RETENTION_DAYS = 7;

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailTokenRepository emailTokenRepository;
    @Mock private UserRepository userRepository;

    private TokenCleanupJobs jobs;

    @BeforeEach
    void setUp() {
        jobs = new TokenCleanupJobs(refreshTokenRepository, emailTokenRepository, userRepository,
                new IdentityProperties("http://localhost:8080", "http://localhost:5173", false, RETENTION_DAYS));
    }

    private User abandonedUser(long id) {
        User user = User.signUpLocally("old" + id + "@albam.dev", "encoded", "테스트", "010-1111-222" + id,
                LocalDate.of(1990, 1, 1), LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void givenCandidates(List<User> users) {
        when(userRepository.findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
                eq(AuthProvider.LOCAL), any(LocalDateTime.class))).thenReturn(users);
    }

    @Test
    void deletesAccountsLeftUnverifiedPastTheRetentionPeriod() {
        List<User> abandoned = List.of(abandonedUser(1L), abandonedUser(2L));
        givenCandidates(abandoned);

        jobs.deleteExpiredUnverifiedAccounts();

        verify(userRepository).deleteAll(abandoned);
    }

    /** 인증 토큰이 계정을 참조하므로 먼저 지워야 삭제가 통과한다. */
    @Test
    @SuppressWarnings("unchecked")
    void removesTheVerificationTokensOfTheDeletedAccounts() {
        givenCandidates(List.of(abandonedUser(1L), abandonedUser(2L)));

        jobs.deleteExpiredUnverifiedAccounts();

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(emailTokenRepository).deleteAllOfUsers(ids.capture());
        assertThat(ids.getValue()).containsExactly(1L, 2L);
    }

    /** 지울 게 없을 때 굳이 삭제 쿼리를 보내지 않는다. */
    @Test
    void doesNothingWhenNoAccountQualifies() {
        givenCandidates(List.of());

        jobs.deleteExpiredUnverifiedAccounts();

        verify(userRepository, never()).deleteAll(any());
        verify(emailTokenRepository, never()).deleteAllOfUsers(anyCollection());
    }

    /** 기간은 실행 시점 기준으로 계산되어야 한다 — 고정 날짜를 쓰면 시간이 지나며 어긋난다. */
    @Test
    void looksBackByTheConfiguredRetentionPeriod() {
        givenCandidates(List.of());
        LocalDateTime before = LocalDateTime.now().minusDays(RETENTION_DAYS);

        jobs.deleteExpiredUnverifiedAccounts();

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
                eq(AuthProvider.LOCAL), threshold.capture());
        assertThat(threshold.getValue())
                .isBetween(before.minusMinutes(1), LocalDateTime.now().minusDays(RETENTION_DAYS).plusMinutes(1));
    }
}
