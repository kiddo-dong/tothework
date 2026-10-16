package com.tothework.workforce.attendance;

import com.tothework.workforce.api.WorkComplianceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceReportEntry(
        Long shiftId,
        Long attendanceId,
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalDate workDate,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        WorkComplianceStatus status,
        long lateMinutes,
        long earlyLeaveMinutes
) {
}
