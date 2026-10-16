plugins {
    id("tothework.core-conventions")
}

dependencies {
    // 탈퇴 이벤트를 받아 상담 이력을 지운다. identity의 계약만 알고 내부는 모른다.
    implementation(project(":identity-api"))

    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-pgvector-store")
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    runtimeOnly("org.postgresql:postgresql")
}
