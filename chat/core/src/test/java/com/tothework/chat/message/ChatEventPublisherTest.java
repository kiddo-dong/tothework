package com.tothework.chat.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.tothework.chat.message.ChatEvent.Type;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 방송은 Redis 발행뿐이다. 발행에 실패하면 보낸 사람에게 알릴 수 있게 예외로 올린다. */
@ExtendWith(MockitoExtension.class)
class ChatEventPublisherTest {

    private static final long STORE_ID = 7L;

    @Mock private StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void publishesTheEventAsJsonToTheStoreChannel() {
        new ChatEventPublisher(redisTemplate, jsonMapper).publish(STORE_ID, event());

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("chat:stores:7"), json.capture());
        ChatEvent decoded = jsonMapper.readValue(json.getValue(), ChatEvent.class);
        assertThat(decoded).isEqualTo(event());
    }

    @Test
    void aRedisFailureBecomesABroadcastFailure() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate)
                .convertAndSend(eq("chat:stores:7"), anyString());

        assertThatThrownBy(() -> new ChatEventPublisher(redisTemplate, jsonMapper).publish(STORE_ID, event()))
                .isInstanceOf(ChatBroadcastException.class);
    }

    static ChatEvent event() {
        LocalDateTime at = LocalDateTime.of(2026, 11, 23, 9, 30);
        return new ChatEvent(Type.CREATED,
                new ChatMessageResponse(1L, STORE_ID, 10L, "알바", null, "안녕하세요", false, at, at));
    }
}
