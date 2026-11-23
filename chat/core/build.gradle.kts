plugins {
    id("tothework.core-conventions")
}

dependencies {
    // 소속 확인(구독·전송 권한)과 매장 삭제 이벤트. chat → store 방향으로만 의존한다.
    implementation(project(":store-api"))
    // 보낸 사람 이름·사진.
    implementation(project(":identity-api"))
    // STOMP over WebSocket. 매장 단체 채팅의 실시간 통로.
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    // 서버가 여러 대일 때 채팅 이벤트를 서버 간에 전달한다(Pub/Sub). 저장은 하지 않는다.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
