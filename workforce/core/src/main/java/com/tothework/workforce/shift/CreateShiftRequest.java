package com.tothework.workforce.shift;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateShiftRequest(
        @NotNull Long storeMemberId,
        @NotNull LocalDate workDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) Integer breakMinutes
) {
}
