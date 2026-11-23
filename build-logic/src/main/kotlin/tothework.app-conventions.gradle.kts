// 실행 모듈용. Spring Boot 플러그인으로 실행 가능한 jar를 만든다.
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("tothework.java-conventions")
    id("tothework.boundary-rules")
    id("org.springframework.boot")
}

// Redis 클라이언트 밑의 Netty가 네이티브 라이브러리를 불러온다(JNI). JDK 24부터 허용 없이 부르면 경고가 나고, 이후
// 버전에서는 막힐 예정이다(JEP 472). classpath의 코드 전체(ALL-UNNAMED)에 허용한다.
val nativeAccess = "ALL-UNNAMED"

// 배포 jar의 매니페스트에 적는다. `java -jar`로 띄우면 실행 옵션 없이도 허용된다. jar를 풀어서 -cp로 띄우는 방식이면
// 매니페스트가 읽히지 않으므로 그때는 --enable-native-access=ALL-UNNAMED를 실행 옵션에 넣는다.
tasks.named<BootJar>("bootJar") {
    manifest {
        attributes("Enable-Native-Access" to nativeAccess)
    }
}

tasks.named<BootRun>("bootRun") {
    jvmArgs("--enable-native-access=$nativeAccess")
}
