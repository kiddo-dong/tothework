package com.tothework.workforce.payroll;

import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.workforce.api.WorkComplianceStatus;
import java.util.List;
import java.util.Map;

/** 사장님용 월간 대시보드: 인건비 합계, 멤버별 비용, 근태 준수 현황. */
public record DashboardResponse(
        int targetYear,
        int targetMonth,
        int activeMemberCount,
        long totalWorkMinutes,
        long totalLaborCost,
        long totalDeduction,
        long totalNetPay,
        Map<WorkComplianceStatus, Long> attendanceSummary,
        List<MemberCostRow> members
) {
    /** 멤버별 월간 근무·비용 요약. 해당 월 비용이 없는 퇴사자는 목록에서 제외된다. */
    public record MemberCostRow(
            Long storeMemberId,
            String userName,
            /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
            String userProfileImageUrl,
            MemberRole role,
            MemberStatus status,
            int hourlyWage,
            long workMinutes,
            long totalPay,
            long netPay
    ) {
    }
}
