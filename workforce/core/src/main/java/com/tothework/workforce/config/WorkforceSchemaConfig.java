package com.tothework.workforce.config;

import com.tothework.shared.ServiceSchema;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** workforce 서비스의 스키마({@code tw_workforce})를 준비한다. 다른 서비스의 마이그레이션은 건드리지 않는다. */
@Configuration(proxyBeanMethods = false)
class WorkforceSchemaConfig {

    static final ServiceSchema SCHEMA = ServiceSchema.of("workforce");

    @Bean
    Flyway workforceFlyway(DataSource dataSource) {
        return SCHEMA.migrate(dataSource);
    }
}
