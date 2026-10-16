package com.tothework.workforce.shift;

import java.time.LocalTime;

public record ShiftTemplateResponse(
        Long id,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        Integer breakMinutes,
        boolean overnight,
        int displayOrder
) {
    public static ShiftTemplateResponse from(ShiftTemplate template) {
        return new ShiftTemplateResponse(template.getId(), template.getName(), template.getStartTime(),
                template.getEndTime(), template.getBreakMinutes(), template.isOvernight(),
                template.getDisplayOrder());
    }
}
