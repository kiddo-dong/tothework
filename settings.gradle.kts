rootProject.name = "tothework-modular"

pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// 서비스 후보마다 계약(api)과 구현(core)을 별도 빌드 모듈로 둔다.
// 프로젝트 이름을 "identity-api"처럼 고유하게 짓는 이유: 실행 jar 안에 같은 이름(api.jar)이 여러 개
// 들어가면 충돌한다.
val serviceCandidates = listOf("identity", "store", "workforce", "operations", "laborqa")
for (service in serviceCandidates) {
    include("$service-api", "$service-core")
    project(":$service-api").projectDir = file("$service/api")
    project(":$service-core").projectDir = file("$service/core")
}

include("shared", "bff", "app")
