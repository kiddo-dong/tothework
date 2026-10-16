package com.tothework.workforce.shift;

import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.workforce.api.ShiftResponse;
import com.tothework.workforce.api.ShiftStatus;
import com.tothework.workforce.config.WorkforceSchema;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 근무 스케줄 한 건 (애그리거트 루트). 멤버와 매장은 id로 참조한다. 값을 바꾸면 호출한 쪽이 저장한다. */
@Getter
@Table(schema = WorkforceSchema.NAME, name = "shifts")
public class Shift {

    @Id
    private Long id;
    private final Long memberId;
    private final Long storeId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftStatus status;
    /** 무급 휴게시간(분). 근무시간 계산에서 제외된다. */
    private int breakMinutes;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    Shift(Long id, Long memberId, Long storeId, LocalDate workDate, LocalTime startTime, LocalTime endTime,
            ShiftStatus status, int breakMinutes, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.storeId = storeId;
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.breakMinutes = breakMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Shift schedule(Long memberId, Long storeId, LocalDate workDate, LocalTime startTime, LocalTime endTime,
            int breakMinutes) {
        validateTimeRange(startTime, endTime);
        return new Shift(null, memberId, storeId, workDate, startTime, endTime, ShiftStatus.SCHEDULED, breakMinutes,
                null, null);
    }

    void update(LocalDate workDate, LocalTime startTime, LocalTime endTime, int breakMinutes, ShiftStatus status) {
        validateTimeRange(startTime, endTime);
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.status = status;
    }

    /** @param user 응답에 이름·사진을 채울 사용자. 못 찾았으면 null */
    public ShiftResponse toResponse(UserSummary user) {
        return new ShiftResponse(id, memberId, user == null ? null : user.name(),
                user == null ? null : user.profileImageUrl(), workDate, startTime, endTime, isOvernight(), breakMinutes,
                status);
    }

    public boolean isCanceled() {
        return status == ShiftStatus.CANCELED;
    }

    /** 자정을 넘기는 야간 근무(예: 23:00~06:00)는 종료 시각이 시작 시각보다 이르게 표현된다. */
    public boolean isOvernight() {
        return endTime.isBefore(startTime);
    }

    public LocalDateTime startDateTime() {
        return workDate.atTime(startTime);
    }

    /** 야간 근무는 종료 시각이 다음날에 속하므로 하루를 더해 정규화한다. */
    public LocalDateTime endDateTime() {
        return (isOvernight() ? workDate.plusDays(1) : workDate).atTime(endTime);
    }

    /** 휴게시간을 제외한 실근무시간(분). */
    public long workMinutes() {
        return Duration.between(startDateTime(), endDateTime()).toMinutes() - breakMinutes;
    }

    private static void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime.equals(endTime)) {
            throw new InvalidRequestException("시작 시각과 종료 시각이 같을 수 없습니다.");
        }
    }
}
