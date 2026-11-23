package com.tothework.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 테스트용 MySQL과 Redis. 기본은 Testcontainers로 매번 빈 컨테이너를 띄운다 (Docker 필요).
 *
 * <p>Docker가 없는 환경에서는 {@code localdb} 프로필로 로컬 MySQL과 로컬 Redis(localhost:6379)를 쓴다. 테이블은
 * 서비스별 {@code tw_*} 스키마에만 생기므로 같은 MySQL의 다른 DB와 섞이지 않는다.
 *
 * <pre>SPRING_PROFILES_ACTIVE=localdb DB_PASSWORD=... ./gradlew test</pre>
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("!localdb")
public class TestDatabase {

    /**
     * root로 접속한다. 서비스마다 Flyway가 자기 스키마({@code tw_*})를 만드는데, 컨테이너의 기본 사용자({@code test})는
     * 자기 DB 하나에만 권한이 있어 스키마를 만들지 못한다. 운영 DB 사용자에게도 스키마 생성 권한이 필요하다.
     *
     * <p>기본 DB는 운영과 같이 {@code tw_shared}다. 이벤트 발행 기록 테이블이 스키마 지정 없이 기본 DB에서 읽힌다.
     */
    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4").withDatabaseName("tw_shared").withUsername("root");
    }

    /** 채팅의 서버 간 전달(Pub/Sub). 테스트에서도 실제 Redis를 거쳐야 발행 → 구독 → 로컬 전송 경로가 검증된다. */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    }
}
