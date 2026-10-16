package com.tothework.identity.auth;

/**
 * profileCompleted가 false면 클라이언트는 추가 정보 입력 화면으로 이동시켜야 한다.
 * 리프레시 토큰은 본문이 아니라 HttpOnly 쿠키(refreshToken)로 내려간다 — XSS로 토큰이 탈취되지 않도록.
 */
public record TokenResponse(String accessToken, boolean profileCompleted) {
}
