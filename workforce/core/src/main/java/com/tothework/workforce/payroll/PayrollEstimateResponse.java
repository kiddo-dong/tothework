package com.tothework.workforce.payroll;

import java.time.LocalDate;

/**
 * 이번 달 예상 급여. 오늘까지의 실제 근태(확정분)에 남은 스케줄을 모두 근무한다고 가정한
 * 예상분을 더해 계산한다. 실제 지급액과 다를 수 있다.
 */
public record PayrollEstimateResponse(
        Long storeMemberId,
        int targetYear,
        int targetMonth,
        LocalDate asOf,
        long regularPay,
        long overtimePay,
        long nightPay,
        long holidayWorkPay,
        long weeklyHolidayPay,
        long leavePay,
        long totalPay,
        long deduction,
        long netPay
) {
}
