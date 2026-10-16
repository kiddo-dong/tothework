package com.tothework.workforce.payroll;

import com.tothework.workforce.api.TaxMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 임금명세서 상세. 근로기준법 시행령상 필수 기재사항인 항목별 금액과
 * 계산 방법(근로시간 수), 공제 내역, 일별 근무 기록을 포함한다.
 */
public record PayslipResponse(
        Long storeMemberId,
        String userName,
        String storeName,
        int targetYear,
        int targetMonth,
        int hourlyWage,
        long totalWorkMinutes,
        long overtimeMinutes,
        long nightMinutes,
        long holidayWorkMinutes,
        long regularPay,
        long overtimePay,
        long nightPay,
        long holidayWorkPay,
        long weeklyHolidayPay,
        long leavePay,
        long totalPay,
        TaxMode taxMode,
        long deduction,
        long netPay,
        List<DailyWorkRecord> dailyRecords
) {
    /** 일별 근무 기록 (해당 월분만). */
    public record DailyWorkRecord(
            LocalDate workDate,
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            int breakMinutes,
            long workMinutes
    ) {
    }
}
