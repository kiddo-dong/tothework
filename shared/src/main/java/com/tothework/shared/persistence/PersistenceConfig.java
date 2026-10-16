package com.tothework.shared.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

/**
 * 서비스 공통 영속성 설정.
 *
 * <p>{@code tw_shared} 스키마에는 모듈 간 이벤트의 발행 기록(Event Publication Registry) 테이블만 둔다.
 * 서비스 데이터는 넣지 않는다. 서비스를 떼어 내면 각 서비스가 자기 발행 기록(아웃박스)을 갖게 된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcAuditing
class PersistenceConfig {

    static final ServiceSchema SCHEMA = ServiceSchema.of("shared");

    @Bean
    Flyway sharedFlyway(DataSource dataSource) {
        return SCHEMA.migrate(dataSource);
    }
}
