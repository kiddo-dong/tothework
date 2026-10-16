package com.tothework.workforce.shift;

import java.util.List;

/** LLM 응답 JSON 최상위 구조. {@link AiShiftProposal} 참고. */
public record AiScheduleDraft(
        List<AiShiftProposal> shifts,
        String note
) {
}
