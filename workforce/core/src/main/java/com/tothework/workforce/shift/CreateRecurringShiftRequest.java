package com.tothework.workforce.shift;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record CreateRecurringShiftRequest(
        @NotNull Long storeMemberId,
        @NotEmpty Set<DayOfWeek> daysOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) Integer breakMinutes,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {
}
