package com.tothework.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tothework.shared.ratelimit.InMemoryRateLimitCounter;
import com.tothework.shared.ratelimit.RateLimitCounter;
import com.tothework.shared.ratelimit.RedisRateLimitCounter;
import com.tothework.store.store.StorePurgeJob;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestSchedulerLocks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 운영은 ALB 뒤 서버 2대다. 서버마다 따로 돌거나 따로 세면 안 되는 것들이 공유 저장소(MySQL·Redis)로 하나가 되는지 본다.
 */
@SpringBootTest
@Import(TestDatabase.class)
class MultiServerSafetyTest {

    @Autowired private LockProvider lockProvider;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private StorePurgeJob storePurgeJob;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private Environment environment;
    @MockitoBean private JavaMailSender mailSender;

    /** 한 서버가 락을 잡고 있는 동안 다른 서버는 같은 작업의 락을 얻지 못한다. */
    @Test
    void onlyOneServerGetsTheSchedulerLock() {
        String name = "test-" + UUID.randomUUID();
        LockConfiguration config = new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);

        Optional<SimpleLock> first = lockProvider.lock(config);
        Optional<SimpleLock> second = lockProvider.lock(config);

        assertThat(first).isPresent();
        assertThat(second).as("다른 서버").isEmpty();
        first.get().unlock();
        assertThat(lockProvider.lock(config)).as("풀린 뒤에는 잡을 수 있다").isPresent();
    }

    /** 실제 예약 작업에 락이 걸린다. 실행하면 공유 락 테이블에 그 작업의 행이 생긴다. */
    @Test
    void scheduledJobsRunUnderTheSharedLock() {
        TestSchedulerLocks.releaseAll(jdbcClient);
        storePurgeJob.purgeExpiredStores();

        long rows = jdbcClient.sql("SELECT COUNT(*) FROM tw_shared.shedlock WHERE name = 'store.purgeExpiredStores'")
                .query(Long.class).single();
        assertThat(rows).isEqualTo(1);
    }

    /** 두 서버의 카운터가 Redis의 같은 키를 함께 센다. 서버마다 따로 세면 한도가 서버 수만큼 느슨해진다. */
    @Test
    void twoServersShareOneRateLimitCount() {
        Clock clock = Clock.systemUTC();
        RateLimitCounter server1 = new RedisRateLimitCounter(redisTemplate, new InMemoryRateLimitCounter(clock), clock);
        RateLimitCounter server2 = new RedisRateLimitCounter(redisTemplate, new InMemoryRateLimitCounter(clock), clock);
        String key = "ip:10.0.0.1|login-" + UUID.randomUUID();

        server1.increment(key, Duration.ofMinutes(10));
        server2.increment(key, Duration.ofMinutes(10));
        RateLimitCounter.Count third = server1.increment(key, Duration.ofMinutes(10));

        assertThat(third.count()).isEqualTo(3);
        assertThat(third.remaining()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(10));
    }

    /** 재시작할 때 미완료 이벤트를 다시 보내지 않는다. 다른 서버가 처리 중인 이벤트까지 두 번 처리하게 된다. */
    @Test
    void outstandingEventsAreNotRepublishedOnRestart() {
        assertThat(environment.getProperty("spring.modulith.events.republish-outstanding-events-on-restart",
                Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.modulith.events.staleness.processing", Duration.class))
                .isPositive();
    }
}
