package com.tothework.workforce.payroll.calc;

import com.tothework.workforce.attendance.Attendance;
import com.tothework.workforce.shift.Shift;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 급여 계산에 들어가는 근무 한 건. 끝난 출퇴근 기록이거나, 예상 급여에서 "이 스케줄대로 일한다"고 가정한 가상 근무다.
 *
 * @param attendanceId 실제 출퇴근 기록이면 그 id, 가정한 근무면 null
 */
public record WorkPeriod(Long attendanceId, Long memberId, LocalDate workDate, LocalDateTime clockInAt,
        LocalDateTime clockOutAt, int breakMinutes) {

    public static WorkPeriod of(Attendance attendance) {
        return new WorkPeriod(attendance.getId(), attendance.getMemberId(), attendance.getWorkDate(),
                attendance.getClockInAt(), attendance.getClockOutAt(), attendance.getBreakMinutes());
    }

    /** 아직 오지 않은 스케줄을 그대로 근무한다고 가정한 기록 (저장하지 않음). */
    public static WorkPeriod assumed(Shift shift) {
        return new WorkPeriod(null, shift.getMemberId(), shift.getWorkDate(), shift.startDateTime(),
                shift.endDateTime(), shift.getBreakMinutes());
    }

    /** 휴게를 뺀 실근무 분. */
    public long workMinutes() {
        return Math.max(Duration.between(clockInAt, clockOutAt).toMinutes() - breakMinutes, 0);
    }
}
