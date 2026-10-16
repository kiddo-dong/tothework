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
    // API 문서(/swagger-ui.html). 기본은 꺼져 있고 SWAGGER_ENABLED=true일 때만 열린다.
    implementation(libs.springdoc.webmvc.ui)
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
    // 요청 하나가 보내는 SQL 수를 센다 (N+1 회귀 검사)
    testImplementation("net.ttddyy:datasource-proxy:1.11.1")
    // 서비스 내부 패키지 방향 검사. Modulith가 쓰는 것과 같은 버전
    testImplementation("com.tngtech.archunit:archunit:1.4.2")
}

// 데이터 이전 스크립트는 모듈 밖(migration/)에 있지만 DataMigrationTest가 읽는다. 바뀌면 테스트를 다시 돌린다.
tasks.test {
    inputs.dir(rootProject.layout.projectDirectory.dir("migration")).withPropertyName("migrationScripts")
}
