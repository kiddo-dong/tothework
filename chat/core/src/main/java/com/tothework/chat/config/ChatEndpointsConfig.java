package com.tothework.chat.config;

import com.tothework.shared.security.PublicEndpoints;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** chat이 HTTP 보안 설정에 여는 경로. */
@Configuration(proxyBeanMethods = false)
class ChatEndpointsConfig {

    /**
     * 웹소켓 핸드셰이크. 브라우저는 핸드셰이크에 Authorization 헤더를 실을 수 없어서, 인증은 HTTP가 아니라 STOMP
     * CONNECT 프레임에서 한다(StompAuthChannelInterceptor). 연결만 열리고 CONNECT 전에는 아무것도 할 수 없다.
     */
    @Bean
    PublicEndpoints chatWebSocketEndpoint() {
        return PublicEndpoints.of("/ws");
    }

    /** 수동 테스트 페이지. 페이지 자체에는 데이터가 없고, 쓰려면 로그인해야 한다. 켰을 때만 연다. */
    @Bean
    @ConditionalOnBooleanProperty("tothework.chat.test-page-enabled")
    PublicEndpoints chatTestPageEndpoint() {
        return PublicEndpoints.of("/chat-test.html");
    }
}
