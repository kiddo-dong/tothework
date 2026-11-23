package com.tothework.chat.message;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 채널 {@code chat:stores:*}로 온 채팅 이벤트를 <b>이 서버에 연결된</b> 구독자에게 보낸다. 로컬 전송은 이곳
 * 한 군데에서만 한다.
 *
 * <p>어느 서버가 발행했든(자기 자신 포함) 모든 서버가 받으므로, 각 서버는 자기에게 붙은 사람에게만 보내면 된다.
 * SimpleBroker는 그 서버의 구독자만 알기 때문에 다른 서버로 새어 나가지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        Long storeId = storeIdOf(channel);
        if (storeId == null) {
            log.warn("알 수 없는 채팅 채널을 무시한다: {}", channel);
            return;
        }
        ChatEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), ChatEvent.class);
        } catch (JacksonException e) {
            log.warn("해석할 수 없는 채팅 이벤트를 무시한다 channel={}", channel, e);
            return;
        }
        messagingTemplate.convertAndSend(ChatEventPublisher.topicOf(storeId), event);
    }

    private static Long storeIdOf(String channel) {
        if (!channel.startsWith(ChatEventPublisher.CHANNEL_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(channel.substring(ChatEventPublisher.CHANNEL_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
