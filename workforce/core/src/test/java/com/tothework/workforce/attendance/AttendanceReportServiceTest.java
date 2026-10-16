package com.tothework.workforce.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.tothework.shared.error.ForbiddenException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.WorkComplianceStatus;
import com.tothework.workforce.employment.Workers;
import com.tothework.workforce.leave.LeaveUsage;
import com.tothework.workforce.leave.LeaveUsageRepository;
import com.tothework.workforce.shift.Shift;
import com.tothework.workforce.shift.ShiftRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceReportServiceTest {

    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private LeaveUsageRepository leaveUsageRepository;
    @Mock
    private StoreAccess storeAccess;
    @Mock
    private Workers workers;

    @InjectMocks
    private AttendanceReportService attendanceReportService;

    private static final Long STORE_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    // 실행 시점과 무관하게 항상 "과거"인 고정 날짜 -> 지각/조퇴/결근 판정이 결정적으로 나온다.
    private static final LocalDate PAST_DATE = LocalDate.of(2026, 1, 5);

    private Membership member;
    private Membership managerRequester;

    private static Membership membership(Long memberId, MemberRole role) {
        return new Membership(memberId, STORE_ID, 100L + memberId, role, MemberStatus.ACTIVE, null,
                LocalDateTime.of(2025, 1, 1, 9, 0), null);
    }

    @BeforeEach
    void setUp() {
        member = membership(MEMBER_ID, MemberRole.STAFF);
        managerRequester = membership(1L, MemberRole.MANAGER);
        lenient().when(storeAccess.requireMember(STORE_ID, USER_ID)).thenReturn(managerRequester);
        lenient().when(leaveUsageRepository.findAllByMemberIdAndLeaveDateBetween(anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void exactMatch_isJudgedNormal() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 0), PAST_DATE.atTime(18, 0));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.NORMAL);
        assertThat(report.get(0).lateMinutes()).isZero();
        assertThat(report.get(0).earlyLeaveMinutes()).isZero();
    }

    @Test
    void lateClockIn_isJudgedLate() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 15), PAST_DATE.atTime(18, 0));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.LATE);
        assertThat(report.get(0).lateMinutes()).isEqualTo(15);
    }

    @Test
    void earlyClockOut_isJudgedEarlyLeave() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 0), PAST_DATE.atTime(17, 30));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.EARLY_LEAVE);
        assertThat(report.get(0).earlyLeaveMinutes()).isEqualTo(30);
    }

    @Test
    void lateAndEarlyLeave_areBothJudged() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 10), PAST_DATE.atTime(17, 40));
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.LATE_AND_EARLY_LEAVE);
    }

    @Test
    void stillClockedIn_isJudgedWorking() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance attendance = attendance(1L, PAST_DATE.atTime(9, 0), null); // 아직 퇴근 안 함
        stub(List.of(shift), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.WORKING);
    }

    @Test
    void noAttendance_pastShift_isJudgedAbsent() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        stub(List.of(shift), List.of());

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.ABSENT);
    }

    @Test
    void noAttendance_butOnApprovedLeave_isJudgedOnLeaveInsteadOfAbsent() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(List.of(shift));
        when(attendanceRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(List.of());
        LeaveUsage leave = LeaveUsage.on(MEMBER_ID, STORE_ID, PAST_DATE);
        when(leaveUsageRepository.findAllByMemberIdAndLeaveDateBetween(MEMBER_ID, PAST_DATE, PAST_DATE))
                .thenReturn(List.of(leave));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.ON_LEAVE);
    }

    @Test
    void futureShift_isSkippedEntirely() {
        LocalDate future = LocalDate.now().plusYears(1);
        Shift shift = shift(future, LocalTime.of(9, 0), LocalTime.of(18, 0));
        when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, future, future)).thenReturn(List.of(shift));
        when(attendanceRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
                MEMBER_ID, future, future)).thenReturn(List.of());
        when(leaveUsageRepository.findAllByMemberIdAndLeaveDateBetween(MEMBER_ID, future, future))
                .thenReturn(List.of());

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, future, future);

        assertThat(report).isEmpty(); // 아직 시작 안 한 스케줄은 결근으로도, 그 무엇으로도 판정하지 않는다
    }

    @Test
    void attendanceWithoutMatchingShift_isJudgedExtra() {
        Attendance attendance = attendance(1L, PAST_DATE.atTime(10, 0), PAST_DATE.atTime(14, 0));
        stub(List.of(), List.of(attendance));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        assertThat(report).hasSize(1);
        assertThat(report.get(0).status()).isEqualTo(WorkComplianceStatus.EXTRA);
        assertThat(report.get(0).shiftId()).isNull();
    }

    @Test
    void whenMultipleAttendancesSameDay_matchesTheOneWithLargestOverlap() {
        Shift shift = shift(PAST_DATE, LocalTime.of(9, 0), LocalTime.of(18, 0));
        Attendance weakOverlap = attendance(1L, PAST_DATE.atTime(7, 0), PAST_DATE.atTime(9, 30)); // 30분 겹침
        Attendance strongOverlap = attendance(2L, PAST_DATE.atTime(9, 0), PAST_DATE.atTime(18, 0)); // 완전 겹침
        stub(List.of(shift), List.of(weakOverlap, strongOverlap));

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE);

        AttendanceReportEntry matchedEntry = report.stream()
                .filter(entry -> entry.shiftId() != null)
                .findFirst().orElseThrow();
        assertThat(matchedEntry.attendanceId()).isEqualTo(2L);
        assertThat(matchedEntry.status()).isEqualTo(WorkComplianceStatus.NORMAL);

        // 매칭에서 밀린 나머지 하나는 EXTRA로 남는다.
        assertThat(report).anySatisfy(entry ->
                assertThat(entry.status()).isEqualTo(WorkComplianceStatus.EXTRA));
    }

    @Test
    void nonManagerCannotViewOtherMembersReport() {
        Membership requestingStaff = membership(30L, MemberRole.STAFF); // MEMBER_ID(10)와 다른 본인 id
        when(storeAccess.requireMember(STORE_ID, USER_ID)).thenReturn(requestingStaff);

        assertThatThrownBy(() -> attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffCanViewTheirOwnReport() {
        when(storeAccess.requireMember(STORE_ID, USER_ID)).thenReturn(member);
        stub(List.of(), List.of());

        List<AttendanceReportEntry> report = attendanceReportService.getReport(
                STORE_ID, USER_ID, MEMBER_ID, PAST_DATE, PAST_DATE); // storeMemberId == 본인 id

        assertThat(report).isEmpty(); // 예외 없이 빈 리포트가 반환되면 통과
    }

    private void stub(List<Shift> shifts, List<Attendance> attendances) {
        lenient().when(shiftRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(shifts);
        lenient().when(attendanceRepository.findAllByMemberIdAndWorkDateBetweenOrderByWorkDateDesc(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(attendances);
        lenient().when(leaveUsageRepository.findAllByMemberIdAndLeaveDateBetween(
                MEMBER_ID, PAST_DATE, PAST_DATE)).thenReturn(List.of());
    }

    private static long nextShiftId = 1;

    private Shift shift(LocalDate date, LocalTime start, LocalTime end) {
        Shift shift = Shift.schedule(MEMBER_ID, STORE_ID, date, start, end, 0);
        ReflectionTestUtils.setField(shift, "id", nextShiftId++);
        return shift;
    }

    private Attendance attendance(long id, LocalDateTime clockIn, LocalDateTime clockOut) {
        Attendance attendance = Attendance.clockIn(MEMBER_ID, STORE_ID, clockIn);
        ReflectionTestUtils.setField(attendance, "id", id);
        if (clockOut != null) {
            attendance.correctTimes(clockIn, clockOut, 0);
        }
        return attendance;
    }
}
