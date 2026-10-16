package com.tothework.workforce.payroll;

import com.tothework.workforce.api.WorkComplianceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 주간 대시보드: 주 단위 법 기준(52시간 상한, 주휴 15시간) 모니터링. 금액은 월간 정산에서만 다룬다. */
public record WeeklyDashboardResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        long totalActualMinutes,
        long totalProjectedMinutes,
        Map<WorkComplianceStatus, Long> complianceSummary,
        List<WeeklyMemberRow> members
) {
    /**
     * 멤버별 주간 현황. projected = 실근무 + 오늘 이후 남은 스케줄.
     * capMinutes는 성인 52시간/연소자 35시간이며, 5인 미만 사업장의 성인은 상한이 없어 null이다.
     */
    public record WeeklyMemberRow(
            Long storeMemberId,
            String userName,
            /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
            String userProfileImageUrl,
            long actualMinutes,
            long scheduledRemainingMinutes,
            long projectedMinutes,
            Long capMinutes,
            boolean overCap,
            boolean weeklyHolidayEligible
    ) {
    }
}
