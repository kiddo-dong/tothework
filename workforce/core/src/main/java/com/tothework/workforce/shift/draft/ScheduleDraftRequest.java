package com.tothework.workforce.shift.draft;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ScheduleDraftRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        /**
         * 자유 텍스트 요구사항. 예: "주말엔 2명 이상", "민수는 화목만 배정". 비워도 된다.
         * 프롬프트에 그대로 들어가 비용이 되므로 길이를 제한한다.
         */
        @Size(max = ScheduleDraftRequest.MAX_REQUIREMENT_LENGTH,
                message = "요구사항은 " + ScheduleDraftRequest.MAX_REQUIREMENT_LENGTH + "자까지 입력할 수 있습니다.")
        String requirement
) {
    public static final int MAX_REQUIREMENT_LENGTH = 1000;
}
