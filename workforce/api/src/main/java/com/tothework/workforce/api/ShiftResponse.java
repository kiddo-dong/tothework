package com.tothework.workforce.api;

import java.time.LocalDate;
import java.time.LocalTime;

/** 스케줄 한 건. 스케줄 API의 응답이자 홈 화면의 "오늘 내 스케줄"이라 계약에 둔다. */
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
}
