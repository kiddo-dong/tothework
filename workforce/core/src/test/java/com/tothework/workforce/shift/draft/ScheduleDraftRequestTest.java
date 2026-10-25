package com.tothework.workforce.shift.draft;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 요구사항은 프롬프트에 그대로 들어가므로 길이를 제한한다. */
class ScheduleDraftRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final LocalDate monday = LocalDate.of(2026, 3, 2);

    @Test
    void anEmptyRequirementIsFine() {
        assertThat(validator.validate(new ScheduleDraftRequest(monday, monday.plusDays(6), null))).isEmpty();
    }

    @Test
    void aTooLongRequirementIsRejected() {
        String requirement = "가".repeat(ScheduleDraftRequest.MAX_REQUIREMENT_LENGTH + 1);

        assertThat(validator.validate(new ScheduleDraftRequest(monday, monday.plusDays(6), requirement)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("requirement");
    }
}
