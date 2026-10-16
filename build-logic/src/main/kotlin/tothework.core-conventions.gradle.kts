// 서비스 후보의 구현 모듈. Spring Data JDBC로 자기 스키마만 다루고, Flyway 마이그레이션도 자기 것만 갖는다.
plugins {
    `java-library`
    id("tothework.java-conventions")
    id("tothework.boundary-rules")
}

val serviceName = project.name.removeSuffix("-core")

dependencies {
    api(project(":$serviceName-api"))
    implementation(project(":shared"))

    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
}
