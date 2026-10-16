package com.tothework.operations;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * operations 안의 여섯 기능(체크리스트·공지·인수인계·매뉴얼·거래처·메뉴)은 서로 모른다. 공통으로 쓰는 것은
 * {@code people}(작성자 이름)·{@code config}(스키마)·{@code api}뿐이다. 서로 참조하지 않아야 나중에 기능 하나를 떼어 내거나
 * 지우기 쉽다.
 */
class OperationsPackagesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.tothework.operations");

    private static final List<String> FEATURES = List.of("checklist", "notice", "handover", "manual", "supplier", "menu");

    @Test
    void featurePackagesDoNotDependOnEachOther() {
        for (String feature : FEATURES) {
            String[] others = FEATURES.stream().filter(other -> !other.equals(feature))
                    .map(other -> "com.tothework.operations." + other + "..").toArray(String[]::new);
            noClasses().that().resideInAPackage("com.tothework.operations." + feature + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(others)
                    .because(feature + "는 다른 운영 기능을 몰라야 한다")
                    .check(classes);
        }
    }
}
