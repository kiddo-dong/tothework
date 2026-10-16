package com.tothework.shared;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * 서비스 후보 하나가 소유하는 MySQL 스키마와 그 마이그레이션.
 *
 * <p>서비스마다 스키마를 따로 두고 Flyway 이력도 스키마 안에 따로 쌓는다. 그래서 한 서비스의 테이블에 다른
 * 서비스가 외래키를 걸거나 JOIN할 수 없고, 나중에 서비스를 떼어 낼 때 스키마를 통째로 옮기면 된다.
 *
 * @param schema   MySQL 스키마(데이터베이스) 이름. 기존 운영 DB와 섞이지 않게 {@code tw_} 접두어를 쓴다
 * @param location 마이그레이션 위치. 서비스 core 모듈의 {@code db/<service>}
 */
public record ServiceSchema(String schema, String location) {

    public static ServiceSchema of(String service) {
        return new ServiceSchema("tw_" + service, "classpath:db/" + service);
    }

    /** 스키마가 없으면 만들고 이 서비스의 마이그레이션만 적용한다. */
    public Flyway migrate(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations(location)
                .load();
        flyway.migrate();
        return flyway;
    }
}
