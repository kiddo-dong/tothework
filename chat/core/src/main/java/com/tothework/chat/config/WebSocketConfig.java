package com.tothework.chat.config;

import com.tothework.chat.stomp.StompAuthChannelInterceptor;
import com.tothework.chat.stomp.StompErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * 매장 단체 채팅의 STOMP 설정.
 *
 * <p>통로: 클라이언트는 {@code /app/...}으로 보내고({@code @MessageMapping}), {@code /topic/...}을 구독해 받는다.
 * 개인 오류는 {@code /user/queue/errors}로 받는다. 브로커는 서버 메모리의 SimpleBroker다. 서버가 여러 대면 서버 간
 * 전달은 Redis가 맡는다(ChatEventPublisher).
 *
 * <p>SockJS는 쓰지 않는다. 대상 브라우저가 모두 WebSocket을 지원하고, 폴링 대체 통로가 없으니 로드밸런서에 스티키
 * 세션이 필요 없다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(ChatProperties.class)
@RequiredArgsConstructor
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 서버 → 클라이언트, 클라이언트 → 서버 하트비트 간격. 로드밸런서·nginx의 유휴 타임아웃(60초)보다 짧아야 한다. */
    static final long HEARTBEAT_MS = 10_000;

    private final ChatProperties properties;
    private final StompAuthChannelInterceptor authInterceptor;
    private final StompErrorHandler errorHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
        // 인터셉터가 던진 인증·인가 실패를 ERROR 프레임으로 바꾼다. ERROR 프레임을 보내면 연결이 끊긴다.
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(chatHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    /** 느린 클라이언트 하나가 서버 메모리와 스레드를 붙잡지 않게 묶는다. 채팅 메시지는 500자라 64KB면 넉넉하다. */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(10_000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(64 * 1024);
    }

    @Bean
    ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
