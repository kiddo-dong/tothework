package com.tothework.chat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOrigins  웹소켓 핸드셰이크를 허용할 브라우저 오리진. 와일드카드 대신 도메인을 나열한다
 * @param testPageEnabled 수동 테스트 페이지(/chat-test.html)를 열지. 운영에서는 끈다
 * @param relay           서버 간 전달 방식. 운영은 {@link Relay#REDIS}다
 * @param serverId        이 서버의 이름. 서버가 여러 대일 때 클라이언트가 어느 서버에 붙었는지 보여 준다
 */
@ConfigurationProperties("tothework.chat")
public record ChatProperties(List<String> allowedOrigins, boolean testPageEnabled, Relay relay, String serverId) {

    public enum Relay {
        /** Redis Pub/Sub으로 모든 서버에 전달한다. 서버가 몇 대든 동작한다. */
        REDIS,
        /**
         * 이 서버의 구독자에게만 보낸다. 서버 1대일 때만 맞다. 서버 2대 재현에서 Redis가 왜 필요한지 보여 주는 비교용이다.
         */
        LOCAL
    }

    public ChatProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        relay = relay == null ? Relay.REDIS : relay;
        serverId = serverId == null || serverId.isBlank() ? "local" : serverId;
    }
}
