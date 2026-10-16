// 홈 화면처럼 여러 서비스의 결과를 조합하는 화면용 API. 서비스가 아니라서 자기 데이터가 없다.
plugins {
    `java-library`
    id("tothework.java-conventions")
    id("tothework.boundary-rules")
}

dependencies {
    implementation(project(":shared"))
    for (service in listOf("identity", "store", "workforce", "operations", "laborqa")) {
        implementation(project(":$service-api"))
    }
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}
