package com.tothework.workforce.payroll;

import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.Payrolls;
import com.tothework.workforce.api.WorkComplianceStatus;
import com.tothework.workforce.attendance.Attendance;
import com.tothework.workforce.attendance.AttendanceReportEntry;
import com.tothework.workforce.attendance.AttendanceReportService;
import com.tothework.workforce.attendance.AttendanceRepository;
import com.tothework.workforce.employment.Worker;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.labor.LaborStandards;
import com.tothework.workforce.leave.LeaveUsage;
import com.tothework.workforce.leave.LeaveUsageRepository;
import com.tothework.workforce.payroll.DailyDashboardResponse.DailyMemberRow;
import com.tothework.workforce.payroll.DashboardResponse.MemberCostRow;
import com.tothework.workforce.payroll.WeeklyDashboardResponse.WeeklyMemberRow;
import com.tothework.workforce.shift.Shift;
import com.tothework.workforce.shift.ShiftRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 집계. 월간(인건비 포함)·일간·주간(시간/준수 중심)을 담당한다.
 *
 * <p>매장 전체를 한 번에 읽고 멤버별로 나눈다. 예전에는 연차 일수를 멤버마다 따로 세서 직원 수만큼 쿼리가 늘었다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreDashboardService {

    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final LeaveUsageRepository leaveUsageRepository;
    private final AttendanceReportService attendanceReportService;
    private final StoreAccess storeAccess;
    private final Workers workers;

    public DashboardResponse getDashboard(Long storeId, Long userId, int year, int month) {
        YearMonth yearMonth = MonthlyPay.monthOf(year, month);
        storeAccess.requireOwnerOrManager(storeId, userId);
        MonthlyCosts costs = monthlyCosts(storeId, yearMonth);
        Map<WorkComplianceStatus, Long> attendanceSummary = complianceSummary(attendanceReportService
                .reportOf(storeId, null, yearMonth.atDay(1), yearMonth.atEndOfMonth()));
        return new DashboardResponse(year, month, costs.activeMemberCount(), costs.totalWorkMinutes(),
                costs.totalLaborCost(), costs.totalDeduction(), costs.totalLaborCost() - costs.totalDeduction(),
                attendanceSummary, costs.rows());
    }

    /** 권한 확인 없는 월 인건비 합계. 홈 화면처럼 부르는 쪽이 이미 확인했을 때 쓴다. 근태 요약은 계산하지 않는다. */
    Payrolls.LaborCost laborCostOf(Long storeId, YearMonth yearMonth) {
        MonthlyCosts costs = monthlyCosts(storeId, yearMonth);
        return new Payrolls.LaborCost(costs.totalLaborCost(), costs.totalLaborCost() - costs.totalDeduction());
    }

    private record MonthlyCosts(int activeMemberCount, long totalWorkMinutes, long totalLaborCost,
            long totalDeduction, List<MemberCostRow> rows) {
    }

    /** 멤버별 월 비용. 매장 전체의 근태·스케줄·연차를 한 번씩 읽고 멤버별로 나눠 계산한다. */
    private MonthlyCosts monthlyCosts(Long storeId, YearMonth yearMonth) {
        LocalDate fetchFrom = MonthlyPay.fetchFrom(yearMonth);
        LocalDate fetchTo = MonthlyPay.fetchTo(yearMonth);

        Map<Long, List<WorkPeriod>> worksByMember = attendanceRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, fetchFrom, fetchTo).stream()
                .filter(Attendance::isDone)
                .map(WorkPeriod::of)
                .collect(Collectors.groupingBy(WorkPeriod::memberId));
        Map<Long, List<Shift>> shiftsByMember = activeShiftsOf(storeId, fetchFrom, fetchTo).stream()
                .collect(Collectors.groupingBy(Shift::getMemberId));
        Map<Long, Long> leaveDaysByMember = leaveUsageRepository
                .findAllByStoreIdAndLeaveDateBetween(storeId, yearMonth.atDay(1), yearMonth.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(LeaveUsage::getMemberId, Collectors.counting()));

        long totalWorkMinutes = 0;
        long totalLaborCost = 0;
        long totalDeduction = 0;
        int activeMemberCount = 0;
        List<MemberCostRow> rows = new ArrayList<>();
        for (Worker worker : workers.allWorkersOf(storeId)) {
            boolean active = worker.membership().isActive();
            if (active) {
                activeMemberCount++;
            }
            MonthlyPay pay = MonthlyPay.calculate(worker.terms(), worker.policy().smallBusiness(), yearMonth,
                    worksByMember.getOrDefault(worker.memberId(), List.of()),
                    shiftsByMember.getOrDefault(worker.memberId(), List.of()),
                    leaveDaysByMember.getOrDefault(worker.memberId(), 0L));
            // 이번 달 비용이 없는 퇴사자는 목록에서 제외한다
            if (pay.totalPay() == 0 && !active) {
                continue;
            }
            long workMinutes = Math.round(pay.result().totalWorkedHours() * 60);
            totalWorkMinutes += workMinutes;
            totalLaborCost += pay.totalPay();
            totalDeduction += pay.deduction();
            rows.add(new MemberCostRow(worker.memberId(), worker.name(), profileImageUrlOf(worker),
                    worker.membership().role(), worker.membership().status(), worker.terms().hourlyWage(),
                    workMinutes, pay.totalPay(), pay.netPay()));
        }
        rows.sort((a, b) -> Long.compare(b.totalPay(), a.totalPay()));
        return new MonthlyCosts(activeMemberCount, totalWorkMinutes, totalLaborCost, totalDeduction, rows);
    }

    /** 일일 대시보드: 그날의 근무·근태 요약 (금액 없음). */
    public DailyDashboardResponse getDailyDashboard(Long storeId, Long userId, LocalDate date) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        List<AttendanceReportEntry> report = attendanceReportService.reportOf(storeId, null, date, date);
        Map<Long, Attendance> attendanceById = attendanceRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, date, date).stream()
                .collect(Collectors.toMap(Attendance::getId, Function.identity()));
        List<DailyMemberRow> rows = report.stream()
                .map(entry -> {
                    Attendance attendance = entry.attendanceId() == null ? null
                            : attendanceById.get(entry.attendanceId());
                    return new DailyMemberRow(entry.storeMemberId(), entry.userName(),
                            entry.userProfileImageUrl(), entry.clockInAt(),
                            entry.clockOutAt(), attendance == null ? null : attendance.getBreakMinutes(),
                            attendance == null ? 0 : workMinutesOf(attendance), entry.status(),
                            entry.lateMinutes(), entry.earlyLeaveMinutes());
                })
                .toList();
        long totalWorkMinutes = rows.stream().mapToLong(DailyMemberRow::workMinutes).sum();
        int workedMemberCount = (int) report.stream()
                .filter(entry -> entry.attendanceId() != null)
                .map(AttendanceReportEntry::storeMemberId)
                .distinct()
                .count();
        return new DailyDashboardResponse(date, totalWorkMinutes, workedMemberCount, complianceSummary(report), rows);
    }

    /** 주간 대시보드: 주 52시간(연소자 35시간) 상한·주휴 15시간 모니터링 (금액 없음). */
    public WeeklyDashboardResponse getWeeklyDashboard(Long storeId, Long userId, LocalDate date) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        LocalDate weekStart = LaborStandards.mondayOfWeek(date);
        LocalDate weekEnd = LaborStandards.sundayOfWeek(date);
        LocalDate today = LocalDate.now();

        // 실근무: 이번 주의 근태 (근무 중이면 현재까지 누적)
        Map<Long, Long> actualByMember = new HashMap<>();
        for (Attendance attendance : attendanceRepository
                .findAllByStoreIdAndWorkDateBetweenOrderByWorkDateDesc(storeId, weekStart, weekEnd)) {
            actualByMember.merge(attendance.getMemberId(), workMinutesOf(attendance), Long::sum);
        }
        // 남은 스케줄: 오늘 이후의 비취소 스케줄
        Map<Long, Long> remainingByMember = new HashMap<>();
        for (Shift shift : activeShiftsOf(storeId, weekStart, weekEnd)) {
            if (shift.getWorkDate().isAfter(today)) {
                remainingByMember.merge(shift.getMemberId(), shift.workMinutes(), Long::sum);
            }
        }

        List<WeeklyMemberRow> rows = new ArrayList<>();
        for (Worker worker : workers.allWorkersOf(storeId)) {
            long actual = actualByMember.getOrDefault(worker.memberId(), 0L);
            long remaining = remainingByMember.getOrDefault(worker.memberId(), 0L);
            if (worker.membership().status() != MemberStatus.ACTIVE && actual == 0 && remaining == 0) {
                continue;
            }
            long projected = actual + remaining;
            Long capMinutes;
            if (LaborStandards.isMinor(worker.birthDate(), weekEnd)) {
                capMinutes = (long) LaborStandards.MINOR_MAX_WEEKLY_WORK_MINUTES;
            } else if (worker.policy().smallBusiness()) {
                capMinutes = null;
            } else {
                capMinutes = (long) LaborStandards.MAX_WEEKLY_WORK_MINUTES;
            }
            rows.add(new WeeklyMemberRow(worker.memberId(), worker.name(), profileImageUrlOf(worker), actual,
                    remaining, projected, capMinutes, capMinutes != null && projected > capMinutes,
                    projected >= LaborStandards.WEEKLY_HOLIDAY_ELIGIBLE_MINUTES));
        }
        rows.sort(Comparator.comparingLong(WeeklyMemberRow::projectedMinutes).reversed());
        Map<WorkComplianceStatus, Long> summary = complianceSummary(
                attendanceReportService.reportOf(storeId, null, weekStart, weekEnd));
        long totalActual = rows.stream().mapToLong(WeeklyMemberRow::actualMinutes).sum();
        long totalProjected = rows.stream().mapToLong(WeeklyMemberRow::projectedMinutes).sum();
        return new WeeklyDashboardResponse(weekStart, weekEnd, totalActual, totalProjected, summary, rows);
    }

    private List<Shift> activeShiftsOf(Long storeId, LocalDate from, LocalDate to) {
        return shiftRepository.findAllByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(storeId, from, to)
                .stream().filter(shift -> !shift.isCanceled()).toList();
    }

    private static Map<WorkComplianceStatus, Long> complianceSummary(List<AttendanceReportEntry> report) {
        return report.stream().collect(Collectors.groupingBy(AttendanceReportEntry::status, Collectors.counting()));
    }

    private static String profileImageUrlOf(Worker worker) {
        return worker.user() == null ? null : worker.user().profileImageUrl();
    }

    /** 실근무시간(분). 퇴근 전이면 현재 시각까지 누적으로 계산한다. */
    private static long workMinutesOf(Attendance attendance) {
        LocalDateTime end = attendance.getClockOutAt() == null ? LocalDateTime.now() : attendance.getClockOutAt();
        return Math.max(0, Duration.between(attendance.getClockInAt(), end).toMinutes() - attendance.getBreakMinutes());
    }
}
