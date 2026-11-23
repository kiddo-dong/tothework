package com.tothework.shared.ratelimit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 호출 제한 카운터. 서버가 여러 대라 Redis에서 함께 세고, Redis가 없으면 서버 메모리로 센다. */
@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

    @Bean
    RateLimitCounter rateLimitCounter(StringRedisTemplate redisTemplate) {
        Clock clock = Clock.systemUTC();
        return new RedisRateLimitCounter(redisTemplate, new InMemoryRateLimitCounter(clock), clock);
    }
}
