package com.tothework.identity.auth;

import com.tothework.identity.config.IdentitySchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 이메일 인증·비밀번호 재설정용 1회성 토큰. 사용자는 id로 참조한다. */
@Getter
@Table(schema = IdentitySchema.NAME, name = "email_tokens")
public class EmailToken {

    @Id
    private Long id;
    private final Long userId;
    private final String token;
    private final EmailTokenType type;
    private final LocalDateTime expiresAt;
    private boolean used;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    EmailToken(Long id, Long userId, String token, EmailTokenType type, LocalDateTime expiresAt, boolean used,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.token = token;
        this.type = type;
        this.expiresAt = expiresAt;
        this.used = used;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static EmailToken issue(Long userId, String token, EmailTokenType type, LocalDateTime expiresAt) {
        return new EmailToken(null, userId, token, type, expiresAt, false, null, null);
    }

    public boolean isUsable() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }

    /** 저장은 호출한 쪽이 한다. */
    void markUsed() {
        this.used = true;
    }
}
