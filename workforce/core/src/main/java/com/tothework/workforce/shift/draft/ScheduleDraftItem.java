package com.tothework.workforce.shift.draft;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/** AI가 제안했거나(응답) 프론트가 확정 요청 시 보내는(요청) 스케줄 초안 한 건. */
public record ScheduleDraftItem(
        @NotNull Long storeMemberId,
        String memberName,
        @NotNull LocalDate workDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Integer breakMinutes
) {
}
