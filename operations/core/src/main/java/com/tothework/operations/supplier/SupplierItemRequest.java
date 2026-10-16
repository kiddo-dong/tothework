package com.tothework.operations.supplier;

import jakarta.validation.constraints.NotBlank;
import java.time.DayOfWeek;
import java.util.Map;

/** 발주 품목 생성·수정 공용 요청. weeklyQuantities 예: {"MONDAY": "10개", "THURSDAY": "2박스"}. */
public record SupplierItemRequest(
        @NotBlank String name,
        String spec,
        String memo,
        Integer displayOrder,
        Map<DayOfWeek, String> weeklyQuantities
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
