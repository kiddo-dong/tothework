package com.tothework.workforce.shift.draft;

import java.time.LocalDate;
import java.time.LocalTime;

public record RejectedScheduleDraftItem(
        Long storeMemberId,
        String memberName,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) {
}
