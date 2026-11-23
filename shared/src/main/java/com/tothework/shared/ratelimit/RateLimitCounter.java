package com.tothework.shared.ratelimit;

import java.time.Duration;

/** 고정 창(window) 호출 횟수 카운터. 서버 한 대 안에서 세거나({@link InMemoryRateLimitCounter}) 여러 서버가 함께 센다. */
public interface RateLimitCounter {

    /**
     * 이번 호출을 더한다.
     *
     * @return 이번 창에서 이 키의 호출 수(이번 호출 포함)와 창이 끝날 때까지 남은 시간
     */
    Count increment(String key, Duration window);

    record Count(long count, Duration remaining) {
    }
}
