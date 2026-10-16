package com.tothework.operations.manual;

import java.time.LocalDateTime;
import java.util.List;

public record ManualResponse(
        Long id,
        String category,
        String title,
        String content,
        List<ManualImageResponse> images,
        String authorName,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
