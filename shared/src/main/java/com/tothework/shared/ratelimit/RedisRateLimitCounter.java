package com.tothework.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 여러 서버가 Redis의 같은 키로 함께 센다. ALB가 요청을 두 서버에 나눠 보내도 한도가 하나로 지켜진다.
 *
 * <p>증가와 만료 설정을 Lua 스크립트 하나로 한다. 따로 부르면 두 서버가 동시에 첫 호출을 낼 때 만료가 빠진 키가 남을
 * 수 있다.
 *
 * <p>Redis에 닿지 못하면 이 서버의 메모리로 센다(서버 한 대일 때와 같은 동작). 장애 중에 요청마다 연결 타임아웃을
 * 기다리지 않게, 실패하면 {@link #RETRY_AFTER_FAILURE} 동안은 Redis를 건너뛴다.
 */
@Slf4j
public class RedisRateLimitCounter implements RateLimitCounter {

    static final String KEY_PREFIX = "ratelimit:";
    static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(30);

    private static final DefaultRedisScript<List> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitCounter fallback;
    private final Clock clock;
    private volatile long skipRedisUntil;

    public RedisRateLimitCounter(StringRedisTemplate redisTemplate, RateLimitCounter fallback, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.fallback = fallback;
        this.clock = clock;
    }

    @Override
    public Count increment(String key, Duration window) {
        if (clock.millis() < skipRedisUntil) {
            return fallback.increment(key, window);
        }
        try {
            List<?> result = redisTemplate.execute(INCREMENT, List.of(KEY_PREFIX + key),
                    String.valueOf(window.toMillis()));
            return new Count(((Number) result.get(0)).longValue(), Duration.ofMillis(((Number) result.get(1)).longValue()));
        } catch (RuntimeException e) {
            skipRedisUntil = clock.millis() + RETRY_AFTER_FAILURE.toMillis();
            log.warn("호출 제한 카운터를 Redis에서 읽지 못해 {}초 동안 이 서버의 메모리로 센다: {}",
                    RETRY_AFTER_FAILURE.toSeconds(), e.getMessage());
            return fallback.increment(key, window);
        }
    }
}
