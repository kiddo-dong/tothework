package com.tothework.chat.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tothework.chat.config.ChatProperties;
import com.tothework.chat.config.ChatProperties.Relay;
import com.tothework.chat.message.ChatEvent.Type;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 방송은 Redis 발행뿐이다(로컬로 직접 보내지 않는다). 발행에 실패하면 보낸 사람에게 알릴 수 있게 예외로 올린다. */
@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    private static final long STORE_ID = 7L;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void publishesTheEventAsJsonToTheStoreChannel() {
        publisher(Relay.REDIS).publish(STORE_ID, event());

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("chat:stores:7"), json.capture());
        ChatEvent decoded = jsonMapper.readValue(json.getValue(), ChatEvent.class);
        assertThat(decoded).isEqualTo(event());
    }

    @Test
    void aRedisFailureBecomesABroadcastFailure() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate)
                .convertAndSend(eq("chat:stores:7"), anyString());

        assertThatThrownBy(() -> publisher(Relay.REDIS).publish(STORE_ID, event()))
                .isInstanceOf(ChatBroadcastException.class);
    }

    /** 비교용 로컬 모드는 Redis를 쓰지 않고 이 서버의 구독자에게만 보낸다. */
    @Test
    void localRelaySendsOnlyToThisServer() {
        publisher(Relay.LOCAL).publish(STORE_ID, event());

        verify(messagingTemplate).convertAndSend("/topic/stores/7/chat", event());
        verifyNoInteractions(redisTemplate);
    }

    private ChatEventPublisher publisher(Relay relay) {
        return new ChatEventPublisher(redisTemplate, messagingTemplate, jsonMapper,
                new ChatProperties(List.of(), false, relay, "test"));
    }

    static ChatEvent event() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 23, 9, 30);
        return new ChatEvent(Type.CREATED,
                new ChatMessageResponse(1L, STORE_ID, 10L, "알바", null, "안녕하세요", false, at, at));
    }
}
