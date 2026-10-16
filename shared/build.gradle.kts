// 서비스들이 공통으로 쓰는 기반 코드. MSA로 나뉘면 각 서비스가 의존하는 공용 라이브러리가 된다.
// 응답 형식·예외·인증 사용자 주입·요청 제한·스키마 마이그레이션처럼 기술적인 것만 두고, 도메인 개념은 두지 않는다.
plugins {
    `java-library`
    id("tothework.java-conventions")
    id("tothework.boundary-rules")
}

dependencies {
    api("org.springframework.modulith:spring-modulith-api")
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.security:spring-security-core")
    api("org.springframework.boot:spring-boot-starter-data-jdbc")
    api("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
}
