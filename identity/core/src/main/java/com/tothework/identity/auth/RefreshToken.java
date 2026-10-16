package com.tothework.identity.auth;

import com.tothework.identity.config.IdentitySchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 발급된 리프레시 토큰의 기록. 이 행이 있어야만 재발급이 되므로, 행을 지우는 것이 곧 토큰 폐기다.
 *
 * <p>토큰 원문이 아니라 SHA-256 해시를 저장한다. DB가 유출되더라도 그대로 쓸 수 있는 토큰이 함께 넘어가지 않게
 * 하기 위함이다. 토큰은 서명된 JWT라 엔트로피가 충분하므로 솔트나 반복 해싱은 필요 없다.
 */
@Getter
@Table(schema = IdentitySchema.NAME, name = "refresh_tokens")
public class RefreshToken {

    @Id
    private Long id;
    private final Long userId;
    /** 토큰 원문의 SHA-256 16진 표현(64자). */
    private final String tokenHash;
    /** 만료 시각. 지난 행을 주기적으로 지우려면 DB에도 있어야 한다. */
    private final LocalDateTime expiresAt;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    RefreshToken(Long id, Long userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static RefreshToken record(Long userId, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(null, userId, tokenHash, expiresAt, null, null);
    }
}
