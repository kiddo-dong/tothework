package com.tothework.chat.message;

import com.tothework.chat.config.ChatProperties;
import com.tothework.chat.config.ChatProperties.Relay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 채팅 이벤트 방송의 유일한 출구. 방송은 반드시 이 클래스를 거친다.
 *
 * <p>Redis 채널 {@code chat:stores:{storeId}}에 <b>발행만</b> 한다. 이 서버의 구독자에게도 직접 보내지 않는다.
 * 모든 서버(자기 자신 포함)의 {@link RedisChatSubscriber}가 받아 각자의 로컬 구독자에게 보낸다. 여기서도 로컬로 보내면
 * 같은 서버에 붙은 사람은 같은 메시지를 두 번 받는다.
 *
 * <p>발행이 실패해도 메시지는 이미 DB에 커밋돼 있다. 예외를 던져 보낸 사람에게 알리고, 다른 사람은 재접속하거나
 * 방에 다시 들어올 때 이전 메시지 조회로 받는다.
 *
 * <p>{@code CHAT_RELAY=local}이면 Redis 없이 이 서버의 구독자에게만 보낸다. 서버 1대에서만 맞는 방식으로, 서버 2대
 * 재현에서 비교용으로 쓴다(deploy/chat-test/README.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    static final String CHANNEL_PREFIX = "chat:stores:";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;
    private final ChatProperties properties;

    public void publish(Long storeId, ChatEvent event) {
        if (properties.relay() == Relay.LOCAL) {
            messagingTemplate.convertAndSend(topicOf(storeId), event);
            return;
        }
        try {
            redisTemplate.convertAndSend(channelOf(storeId), jsonMapper.writeValueAsString(event));
        } catch (RuntimeException e) {
            log.error("채팅 이벤트 발행 실패 store={} type={} messageId={}", storeId, event.type(), event.message().id(), e);
            throw new ChatBroadcastException();
        }
    }

    public static String channelOf(Long storeId) {
        return CHANNEL_PREFIX + storeId;
    }

    public static String topicOf(Long storeId) {
        return "/topic/stores/" + storeId + "/chat";
    }
}
