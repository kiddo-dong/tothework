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

    // 웹·검증·Spring Data JDBC·Flyway는 shared를 통해 들어온다.
    implementation("org.springframework.modulith:spring-modulith-events-api")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
}
