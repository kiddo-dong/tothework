// 서비스 간 계약 모듈. 레코드·이벤트·인터페이스만 둔다. 프레임워크에 묶이지 않도록 의존성을 최소로 한다.
plugins {
    `java-library`
    id("tothework.java-conventions")
    id("tothework.boundary-rules")
}

dependencies {
    // api 패키지를 Spring Modulith의 공개 인터페이스로 표시하는 애너테이션 jar. 런타임 동작은 없다.
    api("org.springframework.modulith:spring-modulith-api")
}
