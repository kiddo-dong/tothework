package com.tothework.operations.supplier;

import jakarta.validation.constraints.NotBlank;

/** 거래처 생성·수정 공용 요청. */
public record SupplierRequest(
        @NotBlank String name,
        @NotBlank String category,
        String siteUrl,
        String phone,
        String memo,
        Integer displayOrder
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
