package com.tothework.operations.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 인수인계 노트 한 건. 인수인계 API의 응답이자 홈 화면의 "최근 인수인계" 항목이라 계약에 둔다. */
public record HandoverNoteResponse(
        Long id,
        Long authorMemberId,
        String authorName,
        /** 작성자 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String authorProfileImageUrl,
        String content,
        LocalDate workDate,
        LocalDateTime createdAt
) {
}
