package com.tothework.workforce.payroll;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.labor.LaborStandards;
import com.tothework.workforce.payroll.PayrollCalculator.PayrollResult;
import com.tothework.workforce.shift.Shift;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 한 멤버의 한 달 급여. 개인 급여·명세서·예상 급여와 월간 대시보드가 같은 계산을 쓴다.
 *
 * @param works 그 달과 겹치는 ISO 주 전체의 근무. 주 단위 판정(주 40시간 연장·주휴수당)에 앞뒤 달의 날짜가 필요하다
 */
record MonthlyPay(YearMonth month, List<WorkPeriod> works, PayrollResult result, long leavePay, long totalPay,
        long deduction) {

    long netPay() {
        return totalPay - deduction;
    }

    /** 요청의 연·월. 범위를 벗어나면 400. */
    static YearMonth monthOf(int year, int month) {
        if (month < 1 || month > 12) {
            throw new InvalidRequestException("월은 1~12 사이여야 합니다.");
        }
        return YearMonth.of(year, month);
    }

    /** 근무·스케줄을 읽어 올 범위의 시작: 그 달 1일이 속한 주의 월요일. */
    static LocalDate fetchFrom(YearMonth month) {
        return LaborStandards.mondayOfWeek(month.atDay(1));
    }

    /** 근무·스케줄을 읽어 올 범위의 끝: 그 달 말일이 속한 주의 일요일. */
    static LocalDate fetchTo(YearMonth month) {
        return LaborStandards.sundayOfWeek(month.atEndOfMonth());
    }

    /**
     * @param shifts    취소되지 않은 스케줄 ({@link #fetchFrom}~{@link #fetchTo}). 주휴수당 개근 판정과 연차수당의 1일
     *                  평균 근무시간에 쓴다
     * @param leaveDays 그 달의 연차 사용 일수
     */
    static MonthlyPay calculate(EmploymentTerms terms, boolean smallBusiness, YearMonth month, List<WorkPeriod> works,
            List<Shift> shifts, long leaveDays) {
        Set<LocalDate> scheduledDates = shifts.stream().map(Shift::getWorkDate).collect(Collectors.toSet());
        PayrollResult result = PayrollCalculator.calculate(works, terms.hourlyWage(), smallBusiness,
                terms.weeklyHolidayDay(), scheduledDates, month);
        long leavePay = leavePay(terms.hourlyWage(), shifts, month, leaveDays);
        long totalPay = result.regularPay() + result.overtimePay() + result.nightPay() + result.holidayWorkPay()
                + result.weeklyHolidayPay() + leavePay;
        return new MonthlyPay(month, works, result, leavePay, totalPay, deduction(terms.taxMode(), totalPay));
    }

    /**
     * 연차수당: 해당 월의 연차 사용일 x 1일 평균 스케줄 근무시간 x 시급. 평균은 그 달의 비취소 스케줄로 계산하고,
     * 스케줄이 없으면 법정 1일 기준(8시간)을 쓴다.
     */
    static long leavePay(int hourlyWage, List<Shift> shifts, YearMonth month, long leaveDays) {
        if (leaveDays == 0) {
            return 0;
        }
        List<Shift> monthShifts = shifts.stream()
                .filter(shift -> YearMonth.from(shift.getWorkDate()).equals(month))
                .toList();
        long scheduledDayCount = monthShifts.stream().map(Shift::getWorkDate).distinct().count();
        double dailyHours = scheduledDayCount == 0
                ? LaborStandards.STANDARD_DAILY_HOURS
                : monthShifts.stream().mapToLong(Shift::workMinutes).sum() / 60.0 / scheduledDayCount;
        return Math.round(leaveDays * dailyHours * hourlyWage);
    }

    static long deduction(TaxMode taxMode, long grossPay) {
        return switch (taxMode) {
            case NONE -> 0;
            case WITHHOLDING_3_3 -> Math.round(grossPay * LaborStandards.WITHHOLDING_TAX_RATE);
            case FOUR_INSURANCES -> Math.round(grossPay * LaborStandards.FOUR_INSURANCES_EMPLOYEE_RATE);
        };
    }
}
