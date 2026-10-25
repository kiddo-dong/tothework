package com.tothework.identity.auth;

import com.tothework.identity.config.IdentitySchema;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface EmailTokenRepository extends ListCrudRepository<EmailToken, Long> {

    String TABLE = IdentitySchema.NAME + ".email_tokens";

    Optional<EmailToken> findByTokenAndType(String token, EmailTokenType type);

    /** 그 시각 이후에 같은 종류의 메일 토큰을 발급했는지. 메일 재발송 간격을 지키는 데 쓴다. */
    boolean existsByUserIdAndTypeAndCreatedAtAfter(Long userId, EmailTokenType type, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE user_id = :userId")
    void deleteAllOfUser(Long userId);

    /** 계정 자동 정리에서 여러 사용자의 토큰을 한 번에 지운다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE user_id IN (:userIds)")
    void deleteAllOfUsers(Collection<Long> userIds);
}
