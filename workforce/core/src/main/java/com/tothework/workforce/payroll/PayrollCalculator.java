package com.tothework.workforce.payroll;

import com.tothework.workforce.labor.LaborStandards;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 근로기준법을 반영한 급여 계산기.
 *
 * <p>입력 근태는 대상 월과 겹치는 ISO 주(월~일) 전체를 포함해야 한다. 주 단위 판정(주 40시간 초과 연장,
 * 주휴수당)은 주 전체 데이터로 하되, 지급액은 대상 월에 속한 날짜의 근무분만 귀속한다. 주휴수당은 그 주의
 * 일요일이 속한 월에 지급한다.
 *
 * <p>포함:
 * <ul>
 *   <li>기본급: 휴게시간을 제외한 실근무시간 x 시급</li>
 *   <li>연장근로수당: 1일 8시간 초과분 + 주 누적(일 단위 연장 제외) 40시간 초과분을 발생한 날짜에 귀속해
 *       1.5배 (같은 시간을 중복 가산하지 않음). 주휴일 근로시간은 휴일가산과의 중복을 피하기 위해 제외</li>
 *   <li>야간근로수당: 22:00~06:00 근무시간 0.5배 가산</li>
 *   <li>휴일근로수당: 주휴일 근무 시 8시간까지 0.5배, 초과분 1.0배 가산 (기본급은 별도 지급)</li>
 *   <li>주휴수당: 주 15시간 이상 근무 + 개근(해당 주의 비취소 스케줄 날짜에 모두 출근 기록 존재) 시
 *       (주 근무시간/40, 최대 1) x 8 x 시급. 스케줄이 없는 주는 개근으로 간주</li>
 *   <li>5인 미만 사업장: 연장·야간·휴일 가산 미적용 (근무시간은 전부 기본급 1배로 지급, 주휴수당은 적용)</li>
 * </ul>
 *
 * <p>제외(추후 확장 대상): 야간수당은 휴게가 야간 시간대에 있었는지 알 수 없어 휴게 차감 없이 출퇴근 시각
 * 겹침으로만 계산한다. 연차수당은 {@link MonthlyPay}가 별도 합산한다.
 */
final class PayrollCalculator {

    private static final double OVERTIME_MULTIPLIER = 1.5;
    private static final double NIGHT_PREMIUM_MULTIPLIER = 0.5;
    private static final double HOLIDAY_PREMIUM_MULTIPLIER = 0.5;
    private static final double HOLIDAY_OVERTIME_PREMIUM_MULTIPLIER = 1.0;
    private static final double WEEKLY_HOLIDAY_ELIGIBLE_HOURS = 15.0;
    private static final double WEEKLY_HOLIDAY_HOURS = 8.0;
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private PayrollCalculator() {
    }

    static PayrollResult calculate(List<WorkPeriod> attendances, int hourlyWage, boolean smallBusiness,
            DayOfWeek weeklyHolidayDay, Set<LocalDate> scheduledDates, YearMonth targetMonth) {
        Set<LocalDate> attendedDates = attendances.stream()
                .map(WorkPeriod::workDate)
                .collect(Collectors.toSet());
        Map<String, List<LocalDate>> scheduledByWeek = scheduledDates.stream()
                .collect(Collectors.groupingBy(PayrollCalculator::weekKey));
        Map<String, List<WorkPeriod>> attendancesByWeek = attendances.stream()
                .collect(Collectors.groupingBy(attendance -> weekKey(attendance.workDate())));

        double regularHours = 0;
        double overtimeHours = 0;
        double nightHours = 0;
        double holidayPremiumHours = 0;
        double totalWorkedHours = 0;
        double holidayWorkHours = 0;
        long weeklyHolidayPay = 0;

        for (Map.Entry<String, List<WorkPeriod>> entry : attendancesByWeek.entrySet()) {
            List<WorkPeriod> weekAttendances = entry.getValue().stream()
                    .sorted(Comparator.comparing(WorkPeriod::clockInAt))
                    .toList();
            double weekTotalHours = 0;
            double cumulativeNonOvertimeHours = 0;

            for (WorkPeriod attendance : weekAttendances) {
                LocalDateTime start = attendance.clockInAt();
                LocalDateTime end = attendance.clockOutAt();
                long workedMinutes = Duration.between(start, end).toMinutes() - attendance.breakMinutes();
                double workedHours = Math.max(workedMinutes, 0) / 60.0;
                boolean inMonth = YearMonth.from(attendance.workDate()).equals(targetMonth);
                boolean holidayWork = weeklyHolidayDay != null
                        && attendance.workDate().getDayOfWeek() == weeklyHolidayDay;

                weekTotalHours += workedHours;
                if (inMonth) {
                    totalWorkedHours += workedHours;
                    nightHours += nightOverlapMinutes(start, end) / 60.0;
                }

                if (holidayWork) {
                    // 휴일근로는 연장 판정에서 제외하고, 기본급 1배 + 가산(8h까지 0.5배, 초과 1.0배)로 지급
                    if (inMonth) {
                        regularHours += workedHours;
                        holidayWorkHours += workedHours;
                        holidayPremiumHours +=
                                Math.min(workedHours, LaborStandards.STANDARD_DAILY_HOURS)
                                        * HOLIDAY_PREMIUM_MULTIPLIER
                                        + Math.max(workedHours - LaborStandards.STANDARD_DAILY_HOURS, 0)
                                        * HOLIDAY_OVERTIME_PREMIUM_MULTIPLIER;
                    }
                    continue;
                }

                double dailyOvertime = Math.max(workedHours - LaborStandards.STANDARD_DAILY_HOURS, 0);
                double nonOvertime = workedHours - dailyOvertime;
                // 일 단위 연장을 제외한 누적이 주 40시간을 넘어서는 부분이 이 날짜의 주 단위 연장
                double weeklyOvertime = Math.min(nonOvertime, Math.max(0,
                        cumulativeNonOvertimeHours + nonOvertime - LaborStandards.STANDARD_WEEKLY_HOURS));
                cumulativeNonOvertimeHours += nonOvertime;

                if (inMonth) {
                    double dayOvertime = dailyOvertime + weeklyOvertime;
                    overtimeHours += dayOvertime;
                    regularHours += workedHours - dayOvertime;
                }
            }

            // 주휴수당은 그 주의 일요일이 속한 월에 귀속시켜 월 간 중복·누락을 막는다
            LocalDate weekSunday = weekAttendances.get(0).workDate().with(DayOfWeek.SUNDAY);
            boolean perfectAttendance = scheduledByWeek.getOrDefault(entry.getKey(), List.of()).stream()
                    .allMatch(attendedDates::contains);
            if (YearMonth.from(weekSunday).equals(targetMonth)
                    && weekTotalHours >= WEEKLY_HOLIDAY_ELIGIBLE_HOURS && perfectAttendance) {
                weeklyHolidayPay += Math.round(
                        Math.min(weekTotalHours, LaborStandards.STANDARD_WEEKLY_HOURS)
                                / LaborStandards.STANDARD_WEEKLY_HOURS * WEEKLY_HOLIDAY_HOURS * hourlyWage);
            }
        }

        long regularPay = Math.round(regularHours * hourlyWage);
        // 5인 미만 사업장은 가산수당 의무가 없다. 연장분도 기본 1배로만 지급한다.
        long overtimePay = smallBusiness
                ? Math.round(overtimeHours * hourlyWage)
                : Math.round(overtimeHours * hourlyWage * OVERTIME_MULTIPLIER);
        long nightPay = smallBusiness ? 0 : Math.round(nightHours * hourlyWage * NIGHT_PREMIUM_MULTIPLIER);
        long holidayWorkPay = smallBusiness ? 0 : Math.round(holidayPremiumHours * hourlyWage);

        return new PayrollResult(regularPay, overtimePay, nightPay, holidayWorkPay, weeklyHolidayPay,
                totalWorkedHours, overtimeHours, nightHours, holidayWorkHours);
    }

    private static long nightOverlapMinutes(LocalDateTime start, LocalDateTime end) {
        long totalMinutes = 0;
        LocalDate day = start.toLocalDate().minusDays(1);
        while (!day.isAfter(end.toLocalDate())) {
            LocalDateTime nightStart = LocalDateTime.of(day, NIGHT_START);
            LocalDateTime nightEnd = LocalDateTime.of(day.plusDays(1), NIGHT_END);
            LocalDateTime overlapStart = start.isAfter(nightStart) ? start : nightStart;
            LocalDateTime overlapEnd = end.isBefore(nightEnd) ? end : nightEnd;
            if (overlapStart.isBefore(overlapEnd)) {
                totalMinutes += Duration.between(overlapStart, overlapEnd).toMinutes();
            }
            day = day.plusDays(1);
        }
        return totalMinutes;
    }

    private static String weekKey(LocalDate date) {
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int weekOfYear = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return weekYear + "-" + weekOfYear;
    }

    /** 금액과 함께 임금명세서의 "계산 방법" 기재에 필요한 시간 집계(대상 월 귀속분)를 담는다. */
    record PayrollResult(long regularPay, long overtimePay, long nightPay, long holidayWorkPay,
            long weeklyHolidayPay, double totalWorkedHours, double overtimeHours, double nightHours,
            double holidayWorkHours) {
    }
}
