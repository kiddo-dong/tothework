package com.tothework.identity.auth;

import com.tothework.identity.config.IdentitySchema;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface EmailTokenRepository extends ListCrudRepository<EmailToken, Long> {

    String TABLE = IdentitySchema.NAME + ".email_tokens";

    Optional<EmailToken> findByTokenAndType(String token, EmailTokenType type);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE user_id = :userId")
    void deleteAllOfUser(Long userId);

    /** 계정 자동 정리에서 여러 사용자의 토큰을 한 번에 지운다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE user_id IN (:userIds)")
    void deleteAllOfUsers(Collection<Long> userIds);
}
