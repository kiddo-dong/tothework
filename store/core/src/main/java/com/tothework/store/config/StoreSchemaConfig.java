package com.tothework.store.config;

import com.tothework.shared.persistence.ServiceSchema;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** store 서비스의 스키마({@code tw_store})를 준비한다. 다른 서비스의 마이그레이션은 건드리지 않는다. */
@Configuration(proxyBeanMethods = false)
class StoreSchemaConfig {

    static final ServiceSchema SCHEMA = ServiceSchema.of("store");

    @Bean
    Flyway storeFlyway(DataSource dataSource) {
        return SCHEMA.migrate(dataSource);
    }
}
