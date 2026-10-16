package com.tothework.identity.auth;

import com.tothework.identity.config.IdentitySchema;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface RefreshTokenRepository extends ListCrudRepository<RefreshToken, Long> {

    String TABLE = IdentitySchema.NAME + ".refresh_tokens";

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 비밀번호 변경·재사용 탐지처럼 그 사용자의 모든 세션을 끊어야 할 때 쓴다. 끊은 개수를 돌려준다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE user_id = :userId")
    int deleteAllOfUser(Long userId);

    /**
     * 토큰을 "가져가는" 삭제. 지운 행 수를 돌려준다. 같은 토큰으로 두 요청이 동시에 오면 둘 다 조회로는 행을
     * 찾지만, 삭제는 한쪽만 1을 받는다 — 먼저 지운 쪽이 커밋할 때까지 늦은 쪽은 기다렸다가 0을 받는다.
     */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE id = :id")
    int deleteClaimed(Long id);

    /** 만료된 행 정리. 검증에는 영향이 없고 테이블이 무한히 커지는 것만 막는다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE expires_at < :threshold")
    int deleteExpiredBefore(LocalDateTime threshold);
}
