package com.tothework.chat.stomp;

import com.tothework.chat.config.ChatProperties;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/** 연결·구독·해제를 남긴다. 메시지 내용은 남기지 않는다. 서버가 여러 대일 때 누가 어디에 붙었는지 여기서 본다. */
@Slf4j
@Component
@RequiredArgsConstructor
class StompSessionEventLogger {

    private final ChatProperties properties;

    @EventListener
    void on(SessionConnectedEvent event) {
        log.info("STOMP connected server={} session={} user={}", properties.serverId(), sessionIdOf(event),
                userOf(event));
    }

    @EventListener
    void on(SessionSubscribeEvent event) {
        log.info("STOMP subscribed session={} user={} destination={}", sessionIdOf(event), userOf(event),
                StompHeaderAccessor.wrap(event.getMessage()).getDestination());
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        log.info("STOMP disconnected session={} user={} status={}", event.getSessionId(), userOf(event),
                event.getCloseStatus());
    }

    private static String sessionIdOf(AbstractSubProtocolEvent event) {
        return StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
    }

    private static String userOf(AbstractSubProtocolEvent event) {
        Principal user = event.getUser();
        return user == null ? "-" : user.getName();
    }
}
