plugins {
    id("tothework.core-conventions")
}

dependencies {
    // 권한 확인·소속 조회·매장 설정. workforce → store 방향으로만 의존한다.
    implementation(project(":store-api"))
}
