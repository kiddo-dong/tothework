package com.tothework.identity.config;

import com.tothework.shared.persistence.ServiceSchema;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** identity 서비스의 스키마({@code tw_identity})를 준비한다. 다른 서비스의 마이그레이션은 건드리지 않는다. */
@Configuration(proxyBeanMethods = false)
class IdentitySchemaConfig {

    static final ServiceSchema SCHEMA = new ServiceSchema(IdentitySchema.NAME, "classpath:db/identity");

    @Bean
    Flyway identityFlyway(DataSource dataSource) {
        return SCHEMA.migrate(dataSource);
    }
}
