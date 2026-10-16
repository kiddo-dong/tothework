package com.tothework.bff.home;

import com.tothework.operations.api.HandoverNoteResponse;
import com.tothework.store.api.MemberRole;
import com.tothework.workforce.api.ShiftResponse;
import com.tothework.workforce.api.WorkComplianceStatus;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 매장 홈 화면. myDay는 모든 역할 공통이고, managerSection은 OWNER/MANAGER일 때만 채워진다 (아니면 null).
 */
public record HomeResponse(
        MemberRole myRole,
        Long myStoreMemberId,
        MyDaySection myDay,
        ManagerSection managerSection
) {

    /** 오늘 나의 하루 (전 역할 공통). */
    public record MyDaySection(
            List<ShiftResponse> todayShifts,
            boolean working,
            LocalDateTime clockInAt,
            long estimatedMonthNetPay,
            long unreadNoticeCount,
            ChecklistProgress openChecklist,
            ChecklistProgress closeChecklist,
            List<HandoverNoteResponse> recentHandoverNotes
    ) {
    }

    public record ChecklistProgress(int done, int total) {
    }

    /** 오늘 매장 운영 현황 + 인건비·처리할 일 (OWNER/MANAGER). */
    public record ManagerSection(
            List<TodayRosterEntry> todayRoster,
            List<TodayOrderItem> todayOrderItems,
            long monthLaborCost,
            long monthNetPay,
            long pendingJoinRequestCount
    ) {
    }

    /** 오늘 근무자 한 명의 상태. compliance가 null이면 아직 시작 전(예정)이다. */
    public record TodayRosterEntry(
            Long shiftId,
            Long storeMemberId,
            String userName,
            /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
            String userProfileImageUrl,
            LocalTime startTime,
            LocalTime endTime,
            WorkComplianceStatus compliance,
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt
    ) {
    }

    /** 오늘 요일에 발주해야 하는 품목. */
    public record TodayOrderItem(
            String supplierName,
            String itemName,
            String spec,
            String quantity
    ) {
    }
}
