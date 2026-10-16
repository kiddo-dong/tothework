package com.tothework.identity.auth;

import com.tothework.identity.config.IdentityProperties;
import com.tothework.identity.user.AuthProvider;
import com.tothework.identity.user.User;
import com.tothework.identity.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 쌓이기만 하는 인증 데이터를 주기적으로 지운다. 삭제는 여러 번 실행해도 결과가 같아 서버가 여러 대여도 괜찮다. */
@Slf4j
@Component
@RequiredArgsConstructor
class TokenCleanupJobs {

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final UserRepository userRepository;
    private final IdentityProperties properties;

    /**
     * 만료된 리프레시 토큰 행. 만료 검증은 이미 JWT와 조회에서 이뤄져 보안상 필요한 작업은 아니고, 로그인할 때마다
     * 행이 쌓이는데 지우는 경로가 로그아웃뿐이라 테이블이 단조 증가하는 것을 막는다. 사용자가 가장 적은 새벽 4시.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteExpiredRefreshTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건 삭제", deleted);
        }
    }

    /**
     * 인증 메일을 열지 않은 채 기간이 지난 로컬 계정. 이메일과 전화번호를 계속 붙잡고 있기 때문이다.
     *
     * <p>지워도 안전한 근거는 <b>미인증 로컬 계정은 로그인할 수 없다</b>는 것이다. 다른 서비스의 데이터를 만들 수
     * 없고, 남는 것은 가입 때 발급한 인증 토큰뿐이다. 리프레시 토큰 정리와 겹치지 않게 4시 30분.
     */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void deleteExpiredUnverifiedAccounts() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(properties.unverifiedRetentionDays());
        List<User> abandoned = userRepository.findByProviderAndEmailVerifiedFalseAndCreatedAtBefore(
                AuthProvider.LOCAL, threshold);
        if (abandoned.isEmpty()) {
            return;
        }
        List<Long> ids = abandoned.stream().map(User::getId).toList();
        // 인증 토큰이 계정을 참조하므로 먼저 지운다.
        emailTokenRepository.deleteAllOfUsers(ids);
        userRepository.deleteAll(abandoned);
        log.info("인증하지 않은 채 {}일이 지난 계정 {}건 삭제", properties.unverifiedRetentionDays(), ids.size());
    }
}
