package com.tothework.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis에 닿지 못해도 호출 제한은 계속 동작하고(서버 메모리), 장애 중에는 요청마다 Redis를 기다리지 않는다. */
@ExtendWith(MockitoExtension.class)
class RedisRateLimitCounterTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock private StringRedisTemplate redisTemplate;
    private final RateLimitInterceptorTest.MutableClock clock = new RateLimitInterceptorTest.MutableClock(Instant.parse("2026-01-05T09:00:00Z"));

    @Test
    void readsCountAndRemainingTimeFromRedis() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(3L, 42_000L));

        RateLimitCounter.Count count = counter().increment("user:1|m", WINDOW);

        assertThat(count.count()).isEqualTo(3);
        assertThat(count.remaining()).isEqualTo(Duration.ofSeconds(42));
    }

    @Test
    void fallsBackToMemoryAndSkipsRedisForAWhileWhenRedisIsDown() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisRateLimitCounter counter = counter();

        assertThat(counter.increment("user:1|m", WINDOW).count()).isEqualTo(1);
        assertThat(counter.increment("user:1|m", WINDOW).count()).as("메모리에서 이어서 센다").isEqualTo(2);
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));

        clock.advance(RedisRateLimitCounter.RETRY_AFTER_FAILURE);
        counter.increment("user:1|m", WINDOW);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    private RedisRateLimitCounter counter() {
        return new RedisRateLimitCounter(redisTemplate, new InMemoryRateLimitCounter(clock), clock);
    }
}
