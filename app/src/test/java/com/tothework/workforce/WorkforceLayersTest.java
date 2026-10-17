package com.tothework.workforce;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * workforce 안의 의존 방향. 서비스 사이 경계는 Gradle 규칙과 {@code ModularityTests}가 지키고, 이 테스트는 한 서비스
 * 안의 패키지 순서를 지킨다.
 *
 * <pre>
 * payroll → attendance → shift · leave → employment → labor
 * </pre>
 *
 * <p>급여는 근태를 읽어 계산하고, 근태 리포트는 스케줄·휴가와 맞춰 보고, 스케줄은 고용 조건(근무 가능 요일·연소자
 * 여부)으로 규칙을 판단한다. 거꾸로 가는 참조가 생기면 나중에 급여만 떼어 내거나 스케줄을 따로 바꾸기 어려워진다.
 * shift와 leave는 서로 모른다. config(스키마 이름·AI 클라이언트)와 api는 어디서나 쓸 수 있다. shift 안에서는
 * template·draft가 본체를 쓰고 그 반대는 없다.
 */
class WorkforceLayersTest {

    private static final String ROOT = "com.tothework.workforce";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void dependenciesPointDownwardOnly() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                // payroll은 다음 단계에서 옮겨 온다. 그때까지 빈 계층을 허용한다.
                .withOptionalLayers(true)
                .layer("Payroll").definedBy(ROOT + ".payroll..")
                .layer("Attendance").definedBy(ROOT + ".attendance..")
                .layer("Shift").definedBy(ROOT + ".shift..")
                .layer("Leave").definedBy(ROOT + ".leave..")
                .layer("Employment").definedBy(ROOT + ".employment..")
                .layer("Labor").definedBy(ROOT + ".labor..")
                .whereLayer("Payroll").mayNotBeAccessedByAnyLayer()
                .whereLayer("Attendance").mayOnlyBeAccessedByLayers("Payroll")
                .whereLayer("Shift").mayOnlyBeAccessedByLayers("Attendance", "Payroll")
                .whereLayer("Leave").mayOnlyBeAccessedByLayers("Attendance", "Payroll")
                .whereLayer("Employment").mayOnlyBeAccessedByLayers("Shift", "Leave", "Attendance", "Payroll")
                .whereLayer("Labor").mayOnlyBeAccessedByLayers("Employment", "Shift", "Leave", "Attendance", "Payroll")
                .check(classes);
    }

    /**
     * shift 안의 하위 기능. 근무유형 템플릿(template)과 AI 초안(draft)은 스케줄 본체(shift)를 쓸 수 있지만, 본체와
     * 다른 패키지는 이 둘을 모른다. 초안은 확정할 때 본체의 검증을 그대로 거친다.
     */
    @Test
    void shiftSubFeaturesAreLeaves() {
        for (String leaf : new String[] {".shift.template..", ".shift.draft.."}) {
            noClasses().that().resideOutsideOfPackage(ROOT + leaf)
                    .should().dependOnClassesThat().resideInAPackage(ROOT + leaf)
                    .check(classes);
        }
    }
}
