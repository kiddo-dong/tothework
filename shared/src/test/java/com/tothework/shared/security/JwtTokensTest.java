package com.tothework.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtTokensTest {

    private static final String SECRET = "test-secret-key-test-secret-key-test-secret-key-0123456789";
    private final JwtTokens jwtTokens = new JwtTokens(new JwtProperties(SECRET, 900_000, 1_209_600_000));

    /**
     * 같은 1초 안에 발급해도 토큰이 달라야 한다. 같으면 회전한 리프레시 토큰이 옛 것과 구별되지 않고, 동시 로그인이
     * 토큰 해시의 유니크 제약에 걸린다. (기존 백엔드에 있던 문제를 통합 테스트가 찾아냈다.)
     */
    @Test
    void tokensIssuedInTheSameSecondAreStillDistinct() {
        String first = jwtTokens.issue(1L, "a@albam.dev", JwtTokens.Type.REFRESH);
        String second = jwtTokens.issue(1L, "a@albam.dev", JwtTokens.Type.REFRESH);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void anAccessTokenIsNotAcceptedAsARefreshToken() {
        String access = jwtTokens.issue(1L, "a@albam.dev", JwtTokens.Type.ACCESS);

        assertThat(jwtTokens.isValid(access, JwtTokens.Type.ACCESS)).isTrue();
        assertThat(jwtTokens.isValid(access, JwtTokens.Type.REFRESH)).isFalse();
        assertThat(jwtTokens.userIdOf(access)).isEqualTo(1L);
        assertThat(jwtTokens.emailOf(access)).isEqualTo("a@albam.dev");
    }

    /** 전환 직후에도 이미 로그인한 사용자가 끊기지 않도록, jti가 없는 기존 형식의 토큰도 받아들인다. */
    @Test
    void acceptsTokensInTheOldFormatWithoutAnId() {
        String legacy = Jwts.builder()
                .subject("5")
                .claim("email", "old@albam.dev")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtTokens.isValid(legacy, JwtTokens.Type.ACCESS)).isTrue();
        assertThat(jwtTokens.userIdOf(legacy)).isEqualTo(5L);
    }
}
