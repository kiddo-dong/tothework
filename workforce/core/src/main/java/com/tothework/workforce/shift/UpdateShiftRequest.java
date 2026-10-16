package com.tothework.workforce.shift;

import com.tothework.workforce.api.ShiftStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateShiftRequest(
        @NotNull LocalDate workDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) Integer breakMinutes,
        @NotNull ShiftStatus status
) {
}
