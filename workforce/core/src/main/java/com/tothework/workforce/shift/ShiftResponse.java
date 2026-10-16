package com.tothework.workforce.shift;

import com.tothework.identity.api.UserSummary;
import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftResponse(
        Long id,
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean overnight,
        int breakMinutes,
        ShiftStatus status) {

    static ShiftResponse of(Shift shift, UserSummary user) {
        return new ShiftResponse(shift.getId(), shift.getMemberId(), user == null ? null : user.name(),
                user == null ? null : user.profileImageUrl(), shift.getWorkDate(), shift.getStartTime(),
                shift.getEndTime(), shift.isOvernight(), shift.getBreakMinutes(), shift.getStatus());
    }
}
