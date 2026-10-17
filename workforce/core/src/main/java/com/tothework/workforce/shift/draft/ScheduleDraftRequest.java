package com.tothework.workforce.shift.draft;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ScheduleDraftRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        /** 자유 텍스트 요구사항. 예: "주말엔 2명 이상", "민수는 화목만 배정". 비워도 된다. */
        String requirement
) {
}
