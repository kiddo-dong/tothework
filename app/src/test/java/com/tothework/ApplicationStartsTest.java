package com.tothework;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.tothework.support.TestDatabase;

/** 실제 MySQL(Testcontainers)에 붙여 앱이 뜨고, 서비스마다 스키마가 따로 만들어지는지 확인한다. */
@SpringBootTest
@Import(TestDatabase.class)
class ApplicationStartsTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void eachServiceGetsItsOwnSchema() {
        var schemas = jdbcClient.sql("SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tw\\_%'")
                .query(String.class)
                .list();

        assertThat(schemas).containsExactlyInAnyOrder(
                "tw_shared", "tw_identity", "tw_store", "tw_workforce", "tw_operations", "tw_laborqa");
    }
}
