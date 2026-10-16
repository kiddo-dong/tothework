package com.tothework.operations.manual;

import java.time.LocalDateTime;

/** 목록용 요약 (본문·이미지 제외). */
public record ManualSummaryResponse(
        Long id,
        String category,
        String title,
        int displayOrder,
        LocalDateTime updatedAt
) {
}
