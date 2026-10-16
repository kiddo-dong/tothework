package com.tothework.workforce.payroll;

import com.tothework.workforce.attendance.WorkComplianceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 일일 대시보드: 그날의 근무시간·근태 요약. 금액은 월간 정산에서만 다룬다. */
public record DailyDashboardResponse(
        LocalDate date,
        long totalWorkMinutes,
        int workedMemberCount,
        Map<WorkComplianceStatus, Long> complianceSummary,
        List<DailyMemberRow> members
) {
    /** 근무(또는 결근 판정) 건별 행. 근무 중이면 clockOutAt이 null이고 workMinutes는 현재까지 누적이다. */
    public record DailyMemberRow(
            Long storeMemberId,
            String userName,
            /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
            String userProfileImageUrl,
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            Integer breakMinutes,
            long workMinutes,
            WorkComplianceStatus compliance,
            long lateMinutes,
            long earlyLeaveMinutes
    ) {
    }
}
