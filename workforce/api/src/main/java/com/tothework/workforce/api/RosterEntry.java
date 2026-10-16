package com.tothework.workforce.api;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 그날 근무자 한 명의 상태. compliance가 null이면 아직 판정 전(시작 전이거나 진행 중인데 출근 기록이 없음)이다.
 */
public record RosterEntry(
        Long shiftId,
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalTime startTime,
        LocalTime endTime,
        WorkComplianceStatus compliance,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt
) {
}
