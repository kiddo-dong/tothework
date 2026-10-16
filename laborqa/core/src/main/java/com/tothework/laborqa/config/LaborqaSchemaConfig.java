package com.tothework.laborqa.config;

import com.tothework.shared.ServiceSchema;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** laborqa 서비스의 스키마({@code tw_laborqa})를 준비한다. 다른 서비스의 마이그레이션은 건드리지 않는다. */
@Configuration(proxyBeanMethods = false)
class LaborqaSchemaConfig {

    static final ServiceSchema SCHEMA = ServiceSchema.of("laborqa");

    @Bean
    Flyway laborqaFlyway(DataSource dataSource) {
        return SCHEMA.migrate(dataSource);
    }
}
