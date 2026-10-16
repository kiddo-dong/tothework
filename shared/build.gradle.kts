// 서비스들이 공통으로 쓰는 기반 코드. MSA로 나뉘면 각 서비스가 의존하는 공용 라이브러리가 된다.
// 도메인 개념은 두지 않는다.
plugins {
    `java-library`
    id("tothework.java-conventions")
    id("tothework.boundary-rules")
}

dependencies {
    api("org.flywaydb:flyway-core")
    api("org.springframework:spring-jdbc")
    api("org.springframework.modulith:spring-modulith-api")
}
