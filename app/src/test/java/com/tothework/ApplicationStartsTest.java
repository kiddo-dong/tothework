package com.tothework;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.mysql.MySQLContainer;

/** 실제 MySQL(Testcontainers)에 붙여 앱이 뜨고, 서비스마다 스키마가 따로 만들어지는지 확인한다. */
@SpringBootTest
@Import(ApplicationStartsTest.Containers.class)
class ApplicationStartsTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        @Bean
        @ServiceConnection
        MySQLContainer mysql() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void eachServiceGetsItsOwnSchema() {
        var schemas = jdbcClient.sql("SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tw\\_%'")
                .query(String.class)
                .list();

        assertThat(schemas).containsExactlyInAnyOrder(
                "tw_identity", "tw_store", "tw_workforce", "tw_operations", "tw_laborqa");
    }
}
