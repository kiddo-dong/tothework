package com.tothework.laborqa.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** 질문 길이는 곧 LLM 비용이라 상한을 둔다. */
class LaborQaRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aQuestionUpToTheLimitIsAccepted() {
        assertThat(validator.validate(new LaborQaRequest("가".repeat(LaborQaRequest.MAX_QUESTION_LENGTH)))).isEmpty();
    }

    @Test
    void aLongerQuestionIsRejectedBeforeCallingTheModel() {
        assertThat(validator.validate(new LaborQaRequest("가".repeat(LaborQaRequest.MAX_QUESTION_LENGTH + 1))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("question");
    }
}
