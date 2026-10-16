// 실행 모듈. 서비스들을 하나의 프로세스로 조립한다. 나중에 서비스를 떼어 내면 그 서비스 전용 app을 하나 더 만든다.
plugins {
    id("tothework.app-conventions")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":bff"))
    for (service in listOf("identity", "store", "workforce", "operations", "laborqa")) {
        implementation(project(":$service-core"))
    }
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 모듈 간 이벤트의 발행 기록을 JDBC(MySQL)에 남긴다.
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-docs")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    // 메일 서버를 가짜로 바꾸려고 JavaMailSender 타입이 필요하다 (identity의 내부 의존성이라 직접 보이지 않는다)
    testImplementation("org.springframework.boot:spring-boot-starter-mail")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
}
