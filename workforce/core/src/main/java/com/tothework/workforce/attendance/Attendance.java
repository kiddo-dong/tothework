package com.tothework.workforce.attendance;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Table;

/** 출퇴근 기록 한 건 (애그리거트 루트). 멤버와 매장은 id로 참조한다. 값을 바꾸면 호출한 쪽이 저장한다. */
@Getter
@Table(schema = WorkforceSchema.NAME, name = "attendances")
public class Attendance {

    @Id
    private Long id;
    private final Long memberId;
    private final Long storeId;
    private LocalDate workDate;
    private LocalDateTime clockInAt;
    private LocalDateTime clockOutAt;
    private AttendanceStatus status;
    /** 무급 휴게시간(분). 급여 계산 시 근무시간에서 제외된다. */
    private int breakMinutes;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    /** DB가 계산하는 생성 컬럼(근무 중 1건 제약용)이라 쓰지 않는다. */
    @ReadOnlyProperty
    private Long workingMemberId;

    @PersistenceCreator
    Attendance(Long id, Long memberId, Long storeId, LocalDate workDate, LocalDateTime clockInAt,
            LocalDateTime clockOutAt, AttendanceStatus status, int breakMinutes, LocalDateTime createdAt,
            LocalDateTime updatedAt, Long workingMemberId) {
        this.id = id;
        this.memberId = memberId;
        this.storeId = storeId;
        this.workDate = workDate;
        this.clockInAt = clockInAt;
        this.clockOutAt = clockOutAt;
        this.status = status;
        this.breakMinutes = breakMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.workingMemberId = workingMemberId;
    }

    static Attendance clockIn(Long memberId, Long storeId, LocalDateTime clockInAt) {
        return new Attendance(null, memberId, storeId, clockInAt.toLocalDate(), clockInAt, null,
                AttendanceStatus.WORKING, 0, null, null, null);
    }

    void clockOut(LocalDateTime clockOutAt, int breakMinutes) {
        if (status == AttendanceStatus.DONE) {
            throw new InvalidRequestException("이미 퇴근 처리된 근무입니다.");
        }
        if (!clockOutAt.isAfter(clockInAt)) {
            throw new InvalidRequestException("퇴근 시각은 출근 시각 이후여야 합니다.");
        }
        this.clockOutAt = clockOutAt;
        this.breakMinutes = breakMinutes;
        this.status = AttendanceStatus.DONE;
    }

    void correctTimes(LocalDateTime clockInAt, LocalDateTime clockOutAt, int breakMinutes) {
        if (clockOutAt != null && !clockOutAt.isAfter(clockInAt)) {
            throw new InvalidRequestException("퇴근 시각은 출근 시각 이후여야 합니다.");
        }
        this.workDate = clockInAt.toLocalDate();
        this.clockInAt = clockInAt;
        this.clockOutAt = clockOutAt;
        this.breakMinutes = clockOutAt == null ? 0 : breakMinutes;
        this.status = clockOutAt == null ? AttendanceStatus.WORKING : AttendanceStatus.DONE;
    }

    public boolean isDone() {
        return status == AttendanceStatus.DONE;
    }
}
