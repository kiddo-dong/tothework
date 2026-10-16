package com.tothework.identity.auth;

/**
 * 로그인·재발급으로 발급한 토큰 묶음 (서비스 내부 결과).
 * 리프레시 토큰은 응답 본문에 내려가지 않고 컨트롤러가 HttpOnly 쿠키로만 전달한다.
 */
public record IssuedTokens(String accessToken, String refreshToken, boolean profileCompleted) {
}
