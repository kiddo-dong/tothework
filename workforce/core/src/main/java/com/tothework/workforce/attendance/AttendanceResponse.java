package com.tothework.workforce.attendance;

import com.tothework.identity.api.UserSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalDate workDate,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        int breakMinutes,
        AttendanceStatus status) {

    static AttendanceResponse of(Attendance attendance, UserSummary user) {
        return new AttendanceResponse(attendance.getId(), attendance.getMemberId(), user == null ? null : user.name(),
                user == null ? null : user.profileImageUrl(), attendance.getWorkDate(), attendance.getClockInAt(),
                attendance.getClockOutAt(), attendance.getBreakMinutes(), attendance.getStatus());
    }
}
