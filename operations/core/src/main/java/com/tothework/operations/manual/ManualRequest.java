package com.tothework.operations.manual;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 매뉴얼 생성·수정 공용 요청. imageKeys는 이미지 업로드 API가 돌려준 S3 key 목록. */
public record ManualRequest(
        @NotBlank String category,
        @NotBlank String title,
        @NotBlank String content,
        Integer displayOrder,
        List<String> imageKeys
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
