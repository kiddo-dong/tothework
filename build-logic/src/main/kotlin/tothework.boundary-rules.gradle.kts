// 서비스 후보 사이의 의존 규칙을 빌드 설정 단계에서 강제한다. 테스트까지 갈 필요도 없이 실패한다.
//
//   - *-api  : 다른 프로젝트에 의존하지 않는다. 서비스 간 계약(레코드·이벤트·인터페이스)만 담는다.
//   - *-core : 자기 api, 다른 서비스의 api, shared에만 의존한다. 다른 서비스의 core는 볼 수 없다.
//   - bff    : api와 shared에만 의존한다. 화면 조합만 하고 어떤 서비스의 내부도 모른다.
//   - app    : 모든 것을 조립하는 실행 모듈이라 예외다.
//
// 이 규칙이 있어서 다른 서비스의 엔티티·저장소는 import 자체가 컴파일되지 않는다.

fun violates(sourcePath: String, targetPath: String): String? {
    val source = sourcePath.removePrefix(":")
    val target = targetPath.removePrefix(":")
    return when {
        source == "app" -> null
        source.endsWith("-api") ->
            "api 모듈은 다른 프로젝트에 의존할 수 없다"
        target.endsWith("-core") && target != source ->
            "다른 서비스의 core에 의존할 수 없다. 그 서비스의 api를 쓸 것"
        target == "bff" || target == "app" ->
            "bff·app에는 의존할 수 없다"
        else -> null
    }
}

configurations.configureEach {
    dependencies.withType<ProjectDependency>().configureEach {
        val reason = violates(project.path, path)
        if (reason != null) {
            throw GradleException("${project.path} -> $path : $reason")
        }
    }
}
