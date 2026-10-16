package com.tothework.workforce.payroll;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.attendance.AttendanceRepository;
import com.tothework.workforce.attendance.AttendanceStatus;
import com.tothework.workforce.employment.Worker;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.leave.LeaveUsageRepository;
import com.tothework.workforce.payroll.PayrollCalculator.PayrollResult;
import com.tothework.workforce.shift.Shift;
import com.tothework.workforce.shift.ShiftRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 멤버 한 명의 월 급여·임금명세서·예상 급여. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    private final AttendanceRepository attendanceRepository;
    private final ShiftRepository shiftRepository;
    private final LeaveUsageRepository leaveUsageRepository;
    private final PayrollRecords payrollRecords;
    private final StoreAccess storeAccess;
    private final Workers workers;

    /** 월 급여를 계산하고 그 결과로 급여 기록을 갱신한다. */
    @Transactional
    public PayrollResponse getPayroll(Long storeId, Long memberId, Long userId, int year, int month) {
        Worker target = authorizedTarget(storeId, memberId, userId);
        MonthlyPay pay = monthlyPayOf(target, MonthlyPay.monthOf(year, month), false);
        payrollRecords.save(target.memberId(), storeId, pay);
        return PayrollResponse.of(target, pay);
    }

    /** 급여명세서 상세: 항목별 금액 + 계산 근거(근로시간) + 공제 + 일별 근무 기록. */
    public PayslipResponse getPayslip(Long storeId, Long memberId, Long userId, int year, int month) {
        Worker target = authorizedTarget(storeId, memberId, userId);
        MonthlyPay pay = monthlyPayOf(target, MonthlyPay.monthOf(year, month), false);
        PayrollResult result = pay.result();
        List<PayslipResponse.DailyWorkRecord> dailyRecords = pay.works().stream()
                .filter(work -> YearMonth.from(work.workDate()).equals(pay.month()))
                .sorted(Comparator.comparing(WorkPeriod::clockInAt))
                .map(work -> new PayslipResponse.DailyWorkRecord(work.workDate(), work.clockInAt(),
                        work.clockOutAt(), work.breakMinutes(), work.workMinutes()))
                .toList();
        return new PayslipResponse(target.memberId(), target.name(), target.policy().name(), year, month,
                target.terms().hourlyWage(),
                Math.round(result.totalWorkedHours() * 60), Math.round(result.overtimeHours() * 60),
                Math.round(result.nightHours() * 60), Math.round(result.holidayWorkHours() * 60),
                result.regularPay(), result.overtimePay(), result.nightPay(), result.holidayWorkPay(),
                result.weeklyHolidayPay(), pay.leavePay(), pay.totalPay(), target.terms().taxMode(),
                pay.deduction(), pay.netPay(), dailyRecords);
    }

    /**
     * 본인 예상 급여: 오늘까지의 실제 근태에, 아직 오지 않은 날짜의 비취소 스케줄을 전부 근무한다고 가정한 가상
     * 근태를 더해 계산한다. 저장하지 않는 조회 전용 추정치다. (미래 스케줄 날짜는 출근한 것으로 간주하므로 주휴수당
     * 개근 요건도 낙관적으로 평가된다)
     */
    public PayrollEstimateResponse estimateMyPayroll(Long storeId, Long userId, int year, int month) {
        Membership me = storeAccess.requireMember(storeId, userId);
        MonthlyPay pay = monthlyPayOf(workers.of(storeId, me.memberId()), MonthlyPay.monthOf(year, month), true);
        PayrollResult result = pay.result();
        return new PayrollEstimateResponse(me.memberId(), year, month, LocalDate.now(),
                result.regularPay(), result.overtimePay(), result.nightPay(), result.holidayWorkPay(),
                result.weeklyHolidayPay(), pay.leavePay(), pay.totalPay(), pay.deduction(), pay.netPay());
    }

    /** 대상 멤버 + 접근 권한 확인 (본인 또는 매장 관리자). */
    private Worker authorizedTarget(Long storeId, Long memberId, Long userId) {
        Membership requester = storeAccess.requireMember(storeId, userId);
        Worker target = workers.of(storeId, memberId);
        if (!requester.memberId().equals(target.memberId()) && !requester.isOwnerOrManager()) {
            throw new InvalidRequestException("본인 또는 매장 관리자만 조회할 수 있습니다.");
        }
        return target;
    }

    /**
     * 그 달과 겹치는 ISO 주 전체의 끝난 근태·비취소 스케줄과 그 달의 연차 일수를 읽어 계산한다.
     * includeFutureScheduleAsWorked가 true면(예상 급여) 아직 오지 않은 스케줄을 출근한 것으로 간주한다.
     */
    private MonthlyPay monthlyPayOf(Worker worker, YearMonth month, boolean includeFutureScheduleAsWorked) {
        LocalDate from = MonthlyPay.fetchFrom(month);
        LocalDate to = MonthlyPay.fetchTo(month);
        List<WorkPeriod> works = new ArrayList<>(attendanceRepository
                .findAllByMemberIdAndStatusAndWorkDateBetween(worker.memberId(), AttendanceStatus.DONE, from, to)
                .stream().map(WorkPeriod::of).toList());
        List<Shift> shifts = shiftRepository
                .findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(worker.memberId(), from, to)
                .stream().filter(shift -> !shift.isCanceled()).toList();
        if (includeFutureScheduleAsWorked) {
            LocalDate today = LocalDate.now();
            shifts.stream().filter(shift -> shift.getWorkDate().isAfter(today))
                    .map(WorkPeriod::assumed).forEach(works::add);
        }
        long leaveDays = leaveUsageRepository.countByMemberIdAndLeaveDateBetween(worker.memberId(), month.atDay(1),
                month.atEndOfMonth());
        return MonthlyPay.calculate(worker.terms(), worker.policy().smallBusiness(), month, works, shifts, leaveDays);
    }
}
