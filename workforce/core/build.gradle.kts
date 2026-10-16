plugins {
    id("tothework.core-conventions")
}

dependencies {
    // 권한 확인·소속 조회·매장 설정. workforce → store 방향으로만 의존한다.
    implementation(project(":store-api"))
    // 응답의 이름·사진, 연소자 판정용 생년월일.
    implementation(project(":identity-api"))
    // AI 스케줄 초안
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
}
