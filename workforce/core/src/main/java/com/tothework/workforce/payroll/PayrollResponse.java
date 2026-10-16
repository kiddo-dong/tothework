package com.tothework.workforce.payroll;

import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.employment.Worker;

public record PayrollResponse(
        Long storeMemberId,
        String userName,
        int targetYear,
        int targetMonth,
        long regularPay,
        long overtimePay,
        long nightPay,
        long holidayWorkPay,
        long weeklyHolidayPay,
        long leavePay,
        long totalPay,
        TaxMode taxMode,
        long deduction,
        long netPay
) {

    static PayrollResponse of(Worker worker, MonthlyPay pay) {
        return new PayrollResponse(worker.memberId(), worker.name(), pay.month().getYear(),
                pay.month().getMonthValue(), pay.result().regularPay(), pay.result().overtimePay(),
                pay.result().nightPay(), pay.result().holidayWorkPay(), pay.result().weeklyHolidayPay(),
                pay.leavePay(), pay.totalPay(), worker.terms().taxMode(), pay.deduction(), pay.netPay());
    }
}
