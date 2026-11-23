package com.tothework.chat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOrigins 웹소켓 핸드셰이크를 허용할 브라우저 오리진. 와일드카드 대신 도메인을 나열한다
 * @param testPageEnabled 수동 테스트 페이지(/chat-test.html)를 열지. 운영에서는 끈다
 */
@ConfigurationProperties("tothework.chat")
public record ChatProperties(List<String> allowedOrigins, boolean testPageEnabled) {

    public ChatProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
