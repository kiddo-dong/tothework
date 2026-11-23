package com.tothework.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이 서버의 메모리에서 센다. 서버가 여러 대면 한도가 서버 수만큼 느슨해지므로 운영에서는 {@link RedisRateLimitCounter}가
 * 쓰고, 이것은 Redis에 닿지 못할 때의 대체와 테스트에 쓴다.
 */
public class InMemoryRateLimitCounter implements RateLimitCounter {

    /** 이 수를 넘으면 만료된 항목을 청소한다 (메모리 무한 증가 방지). */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimitCounter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Count increment(String key, Duration window) {
        long now = clock.millis();
        sweepIfCrowded(now);
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || now >= existing.expiresAt()
                        ? new Window(now + window.toMillis(), 1)
                        : new Window(existing.expiresAt(), existing.count() + 1));
        return new Count(current.count(), Duration.ofMillis(current.expiresAt() - now));
    }

    private void sweepIfCrowded(long now) {
        if (windows.size() < SWEEP_THRESHOLD) {
            return;
        }
        windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
    }

    private record Window(long expiresAt, int count) {
    }
}
