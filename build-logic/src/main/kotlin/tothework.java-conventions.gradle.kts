// 모든 모듈에 공통인 Java 설정: JDK 25, Spring BOM, JUnit 5.
plugins {
    java
}

group = "com.tothework"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    for (bom in listOf("spring-boot-bom", "spring-modulith-bom", "spring-ai-bom")) {
        val platformDependency = platform(libs.findLibrary(bom).get())
        for (configuration in listOf("implementation", "compileOnly", "annotationProcessor",
                "testImplementation", "testCompileOnly", "testAnnotationProcessor")) {
            add(configuration, platformDependency)
        }
    }

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Redis 클라이언트 밑의 Netty가 네이티브 라이브러리를 불러온다(JNI). JDK 24부터 이걸 허용하지 않으면 경고가 나고,
    // 이후 버전에서는 막힐 예정이다(JEP 472). classpath의 코드 전체(ALL-UNNAMED)에 허용한다.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
