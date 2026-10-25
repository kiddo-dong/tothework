package com.tothework.workforce.payroll.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.shift.Shift;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 계산기 바깥의 합산: 연차수당·공제·조회 범위. 계산기 자체는 {@link PayrollCalculatorTest}가 본다. */
class MonthlyPayTest {

    private static final YearMonth JANUARY_2026 = YearMonth.of(2026, 1);
    private static final int WAGE = 10_000;

    @Test
    void leavePayUsesTheAverageScheduledDayOfThatMonth() {
        // 1월 스케줄: 4시간 하루, 6시간 하루 → 1일 평균 5시간. 12월 말 스케줄은 평균에 넣지 않는다.
        List<Shift> shifts = List.of(
                shift(LocalDate.of(2025, 12, 29), 10, 20),
                shift(LocalDate.of(2026, 1, 5), 10, 14),
                shift(LocalDate.of(2026, 1, 6), 10, 16));

        assertThat(MonthlyPay.leavePay(WAGE, shifts, JANUARY_2026, 2)).isEqualTo(2 * 5 * WAGE);
    }

    @Test
    void leavePayFallsBackToEightHoursWithoutSchedules() {
        assertThat(MonthlyPay.leavePay(WAGE, List.of(), JANUARY_2026, 1)).isEqualTo(8 * WAGE);
    }

    @Test
    void deductionFollowsTheTaxMode() {
        assertThat(MonthlyPay.deduction(TaxMode.NONE, 1_000_000)).isZero();
        assertThat(MonthlyPay.deduction(TaxMode.WITHHOLDING_3_3, 1_000_000)).isEqualTo(33_000);
        assertThat(MonthlyPay.deduction(TaxMode.FOUR_INSURANCES, 1_000_000)).isEqualTo(97_170);
    }

    @Test
    void totalAddsLeavePayAndNetSubtractsTheDeduction() {
        EmploymentTerms terms = new EmploymentTerms(1L, 1L, WAGE, TaxMode.WITHHOLDING_3_3, Set.of(), null);

        MonthlyPay pay = MonthlyPay.calculate(terms, false, JANUARY_2026, List.of(), List.of(),
                Set.of(LocalDate.of(2026, 1, 7)));

        assertThat(pay.leavePay()).isEqualTo(80_000);
        assertThat(pay.totalPay()).isEqualTo(80_000);
        assertThat(pay.deduction()).isEqualTo(2_640);
        assertThat(pay.netPay()).isEqualTo(77_360);
    }

    /** 연차수당은 그 달 날짜만 센다. 앞뒤 주의 연차는 개근 판정에만 쓰인다. */
    @Test
    void leaveOutsideTheMonthIsNotPaidThisMonth() {
        EmploymentTerms terms = new EmploymentTerms(1L, 1L, WAGE, TaxMode.NONE, Set.of(), null);

        MonthlyPay pay = MonthlyPay.calculate(terms, false, JANUARY_2026, List.of(), List.of(),
                Set.of(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 2, 1)));

        assertThat(pay.leavePay()).isZero();
    }

    /**
     * 연차를 쓴 날은 결근이 아니다. 1월 2주차(5~9일) 스케줄 5일 중 하루를 연차로 쉬고 나머지 4일을 4시간씩(16시간)
     * 일했다. 스케줄을 취소하지 않았어도 주휴수당이 나와야 한다 (16/40 × 8시간 × 시급).
     */
    @Test
    void aLeaveDayDoesNotBreakPerfectAttendanceForTheWeeklyHolidayPay() {
        EmploymentTerms terms = new EmploymentTerms(1L, 1L, WAGE, TaxMode.NONE, Set.of(), null);
        List<Shift> shifts = new java.util.ArrayList<>();
        List<WorkPeriod> works = new java.util.ArrayList<>();
        for (int day = 5; day <= 9; day++) {
            LocalDate date = LocalDate.of(2026, 1, day);
            shifts.add(shift(date, 10, 14));
            if (day != 7) {
                works.add(new WorkPeriod((long) day, 1L, date, date.atTime(10, 0), date.atTime(14, 0), 0));
            }
        }

        MonthlyPay pay = MonthlyPay.calculate(terms, false, JANUARY_2026, works, shifts,
                Set.of(LocalDate.of(2026, 1, 7)));

        assertThat(pay.result().weeklyHolidayPay()).isEqualTo(Math.round(16 / 40.0 * 8 * WAGE));
        assertThat(pay.leavePay()).isEqualTo(4 * WAGE);
    }

    /** 연차가 아닌데 출근하지 않은 날은 여전히 결근이라 주휴수당이 없다. */
    @Test
    void anUnexcusedAbsenceStillBreaksPerfectAttendance() {
        EmploymentTerms terms = new EmploymentTerms(1L, 1L, WAGE, TaxMode.NONE, Set.of(), null);
        List<Shift> shifts = new java.util.ArrayList<>();
        List<WorkPeriod> works = new java.util.ArrayList<>();
        for (int day = 5; day <= 9; day++) {
            LocalDate date = LocalDate.of(2026, 1, day);
            shifts.add(shift(date, 10, 14));
            if (day != 7) {
                works.add(new WorkPeriod((long) day, 1L, date, date.atTime(10, 0), date.atTime(14, 0), 0));
            }
        }

        MonthlyPay pay = MonthlyPay.calculate(terms, false, JANUARY_2026, works, shifts, Set.of());

        assertThat(pay.result().weeklyHolidayPay()).isZero();
    }

    @Test
    void fetchRangeCoversTheWholeIsoWeeksAroundTheMonth() {
        // 2026-01-01은 목요일, 2026-01-31은 토요일
        assertThat(MonthlyPay.fetchFrom(JANUARY_2026)).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(MonthlyPay.fetchTo(JANUARY_2026)).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void monthOutsideOneToTwelveIsRejected() {
        assertThatThrownBy(() -> MonthlyPay.monthOf(2026, 13))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("월은 1~12 사이여야 합니다.");
    }

    private static Shift shift(LocalDate day, int startHour, int endHour) {
        return Shift.schedule(1L, 1L, day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), 0);
    }
}
