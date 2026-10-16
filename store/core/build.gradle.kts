plugins {
    id("tothework.core-conventions")
}

dependencies {
    // 사람 이름·사진을 보여 주고, 탈퇴를 막을 조건을 알려 주고, 탈퇴 이벤트를 받는다.
    implementation(project(":identity-api"))
}
