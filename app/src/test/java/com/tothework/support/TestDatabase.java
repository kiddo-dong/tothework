package com.tothework.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 테스트용 MySQL. 기본은 Testcontainers로 매번 빈 MySQL을 띄운다 (Docker 필요).
 *
 * <p>Docker가 없는 환경에서는 {@code localdb} 프로필로 로컬 MySQL을 쓴다. 테이블은 서비스별 {@code tw_*} 스키마에만
 * 생기므로 같은 MySQL의 다른 DB와 섞이지 않는다.
 *
 * <pre>SPRING_PROFILES_ACTIVE=localdb DB_PASSWORD=... ./gradlew test</pre>
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("!localdb")
public class TestDatabase {

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4");
    }
}
