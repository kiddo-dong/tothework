package com.tothework.shared.scheduling;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 예약 작업 락. 서버가 여러 대(ALB 뒤 EC2 2대)라 {@code @Scheduled} 작업이 서버마다 같은 시각에 돈다. 작업에
 * {@code @SchedulerLock}을 붙이면 공유 MySQL의 {@code tw_shared.shedlock} 행을 먼저 잡은 한 대만 실행한다.
 *
 * <p>시각은 DB 시계를 쓴다({@code usingDbTime}). 서버끼리 시계가 조금 어긋나도 락 만료 판단이 한곳에서 이뤄진다.
 * 기본 최대 보유 시간(30분)은 작업 중에 서버가 죽었을 때 락이 풀리는 시간이다.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class SchedulerLockConfig {

    static final String TABLE = "tw_shared.shedlock";

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName(TABLE)
                .usingDbTime()
                .build());
    }
}
