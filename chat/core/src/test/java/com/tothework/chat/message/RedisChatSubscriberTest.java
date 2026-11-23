package com.tothework.chat.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Redis로 온 이벤트를 채널의 매장 id에 맞는 로컬 구독 주소로 보낸다. 알 수 없는 것은 버린다. */
@ExtendWith(MockitoExtension.class)
class RedisChatSubscriberTest {

    private static final byte[] PATTERN = "chat:stores:*".getBytes(StandardCharsets.UTF_8);

    @Mock private SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void forwardsToTheLocalTopicOfTheStoreInTheChannel() {
        ChatEvent event = ChatEventPublisherTest.event();

        subscriber().onMessage(message("chat:stores:7", jsonMapper.writeValueAsString(event)), PATTERN);

        verify(messagingTemplate).convertAndSend("/topic/stores/7/chat", event);
    }

    @Test
    void ignoresUnknownChannelsAndBrokenPayloads() {
        String json = jsonMapper.writeValueAsString(ChatEventPublisherTest.event());

        subscriber().onMessage(message("chat:stores:abc", json), PATTERN);
        subscriber().onMessage(message("other:7", json), PATTERN);
        subscriber().onMessage(message("chat:stores:7", "{not json"), PATTERN);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    private RedisChatSubscriber subscriber() {
        return new RedisChatSubscriber(messagingTemplate, jsonMapper);
    }

    private static DefaultMessage message(String channel, String body) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }
}
