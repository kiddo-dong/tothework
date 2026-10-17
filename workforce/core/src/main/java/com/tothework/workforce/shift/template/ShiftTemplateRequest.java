package com.tothework.workforce.shift.template;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record ShiftTemplateRequest(
        @NotBlank String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) Integer breakMinutes,
        Integer displayOrder
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
