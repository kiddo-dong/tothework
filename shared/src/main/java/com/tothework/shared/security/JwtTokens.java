package com.tothework.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * JWT의 형식을 한 곳에서 정한다. subject는 userId, {@code email}·{@code type} 클레임을 담는다 (기존과 같다).
 * 여기에 토큰마다 고유한 {@code jti}를 더했다.
 *
 * <p>모든 서비스가 검증에 쓰고, identity만 {@link #issue}로 발급한다. 형식이 한 클래스에 있으니 발급과 검증이
 * 어긋날 일이 없다.
 */
public class JwtTokens {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TOKEN_TYPE = "type";

    public enum Type {
        ACCESS("access"), REFRESH("refresh");

        private final String claim;

        Type(String claim) {
            this.claim = claim;
        }
    }

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokens(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
    }

    public String issue(Long userId, String email, Type type) {
        long lifetimeMs = type == Type.ACCESS
                ? properties.accessTokenExpirationMs() : properties.refreshTokenExpirationMs();
        Date now = new Date();
        return Jwts.builder()
                // 토큰마다 고유 id를 넣는다. 없으면 같은 1초 안에 발급한 두 토큰이 글자까지 같아져, 회전한 토큰이
                // 옛 토큰과 구별되지 않고 같은 사람의 동시 로그인이 리프레시 토큰 해시의 유니크 제약에 걸린다.
                // 검증할 때 이 값을 요구하지는 않아, 이 클레임이 없는 기존 토큰도 그대로 통한다.
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_TYPE, type.claim)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + lifetimeMs))
                .signWith(key)
                .compact();
    }

    /** 서명과 만료가 유효하고 원하는 종류의 토큰일 때만 내용을 돌려준다. */
    public boolean isValid(String token, Type type) {
        try {
            return type.claim.equals(claims(token).get(CLAIM_TOKEN_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long userIdOf(String token) {
        return Long.valueOf(claims(token).getSubject());
    }

    public String emailOf(String token) {
        return claims(token).get(CLAIM_EMAIL, String.class);
    }

    /** 리프레시 토큰을 DB에 기록할 때 행의 만료를 토큰과 정확히 맞추는 데 쓴다. */
    public LocalDateTime expiresAtOf(String token) {
        return LocalDateTime.ofInstant(claims(token).getExpiration().toInstant(), ZoneId.systemDefault());
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
