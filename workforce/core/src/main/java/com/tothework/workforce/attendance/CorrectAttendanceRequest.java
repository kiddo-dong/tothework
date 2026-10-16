package com.tothework.workforce.attendance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CorrectAttendanceRequest(
        @NotNull LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        @Min(0) Integer breakMinutes
) {
}
