plugins {
    id("tothework.core-conventions")
}

dependencies {
    // 권한 확인·소속 조회. operations → store 방향으로만 의존한다.
    implementation(project(":store-api"))
    // 작성자·확인자 이름과 사진.
    implementation(project(":identity-api"))
}
