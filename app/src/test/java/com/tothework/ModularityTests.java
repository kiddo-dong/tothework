package com.tothework;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * 서비스 후보(모듈) 사이의 경계 검증.
 *
 * <p>다른 서비스의 core에 대한 의존은 Gradle 빌드 규칙(tothework.boundary-rules)이 이미 컴파일 단계에서 막는다.
 * 여기서는 그 위에서 순환과, 같은 프로세스 안에서만 가능한 우회(리플렉션이 아닌 일반 참조)를 한 번 더 확인한다.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(TotheworkApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
