package com.tothework.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 형식과 비밀키를 기존 백엔드와 같게 두어, 전환해도 이미 로그인한 사용자의 토큰이 그대로 통한다.
 *
 * @param secret                 HMAC 서명 키
 * @param accessTokenExpirationMs  액세스 토큰 수명
 * @param refreshTokenExpirationMs 리프레시 토큰 수명. 쿠키 수명도 이 값을 따른다
 */
@ConfigurationProperties("tothework.jwt")
public record JwtProperties(String secret, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
}
