package com.tothework.operations.notice;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long id,
        String title,
        String content,
        String authorName,
        /** 작성자 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String authorProfileImageUrl,
        LocalDateTime createdAt,
        long readCount,
        boolean readByMe
) {
}
