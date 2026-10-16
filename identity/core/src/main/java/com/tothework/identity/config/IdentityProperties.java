package com.tothework.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl                  이 API 서버의 주소. 메일의 인증 링크가 향한다
 * @param frontendUrl              사용자가 보는 화면 주소. 인증 후 이동, 비밀번호 재설정 폼
 * @param cookieSecure             리프레시 토큰 쿠키의 Secure. 배포(https)에서는 true여야 쿠키가 전송된다
 * @param unverifiedRetentionDays  인증 메일을 열지 않은 로컬 계정을 지우기까지 기다리는 날
 */
@ConfigurationProperties("tothework.identity")
public record IdentityProperties(String baseUrl, String frontendUrl, boolean cookieSecure,
        int unverifiedRetentionDays) {
}
