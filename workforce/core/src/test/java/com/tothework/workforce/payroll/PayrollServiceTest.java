package com.tothework.workforce.payroll;

import static com.tothework.workforce.support.TestWorkers.MEMBER_ID;
import static com.tothework.workforce.support.TestWorkers.STORE_ID;
import static com.tothework.workforce.support.TestWorkers.aWorker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.attendance.AttendanceRepository;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.leave.LeaveUsage;
import com.tothework.workforce.leave.LeaveUsageRepository;
import com.tothework.workforce.shift.Shift;
import com.tothework.workforce.shift.ShiftRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 예상 급여: 끝난 근태에 남은 스케줄을 더하되, 연차를 쓴 날은 근무로 세지 않는다. */
@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    /** TestWorkers의 시급. */
    private static final int WAGE = 10_030;
    /** 오늘보다 뒤라 모든 스케줄이 "남은 스케줄"이 되는 달. */
    private static final YearMonth NEXT_MONTH = YearMonth.now().plusMonths(1);

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private LeaveUsageRepository leaveUsageRepository;
    @Mock private PayrollRecords payrollRecords;
    @Mock private StoreAccess storeAccess;
    @Mock private Workers workers;
    @InjectMocks private PayrollService payrollService;

    private final LocalDate workDay = NEXT_MONTH.atDay(10);
    private final LocalDate leaveDay = NEXT_MONTH.atDay(11);

    @BeforeEach
    void setUp() {
        when(workers.of(STORE_ID, MEMBER_ID)).thenReturn(aWorker().build());
        when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(eq(MEMBER_ID), any(),
                any())).thenReturn(List.of(fourHourShift(workDay), fourHourShift(leaveDay)));
    }

    /**
     * 4시간 스케줄 두 날 중 하루는 연차다. 근무 4시간 + 연차수당 4시간이어야 한다. 예전에는 연차일 스케줄을 근무한 것으로
     * 가정하고 연차수당도 붙여, 그날을 두 번 셌다(12시간분).
     */
    @Test
    void aFutureLeaveDayIsPaidOnceAsLeaveRatherThanAlsoAsWork() {
        when(leaveUsageRepository.findAllByMemberIdAndLeaveDateBetween(eq(MEMBER_ID), any(), any()))
                .thenReturn(List.of(LeaveUsage.on(MEMBER_ID, STORE_ID, leaveDay)));

        long netPay = payrollService.estimateNetPay(STORE_ID, MEMBER_ID, NEXT_MONTH);

        assertThat(netPay).isEqualTo(4L * WAGE + 4L * WAGE);
    }

    @Test
    void withoutLeaveEveryRemainingShiftCountsAsWork() {
        long netPay = payrollService.estimateNetPay(STORE_ID, MEMBER_ID, NEXT_MONTH);

        assertThat(netPay).isEqualTo(8L * WAGE);
    }

    private static Shift fourHourShift(LocalDate day) {
        return Shift.schedule(MEMBER_ID, STORE_ID, day, LocalTime.of(10, 0), LocalTime.of(14, 0), 0);
    }
}
