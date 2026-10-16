package com.tothework.operations.notice;

import java.time.LocalDateTime;

/** 공지별 멤버 확인 현황. readAt이 null이면 아직 확인하지 않은 것. */
public record NoticeReadStatusResponse(
        Long storeMemberId,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        LocalDateTime readAt
) {
}
