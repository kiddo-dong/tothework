package com.tothework.operations.menu;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** 재료 단가 생성·수정 공용 요청. */
public record MenuIngredientRequest(
        @NotBlank String name,
        String productInfo,
        @PositiveOrZero int price,
        @Positive double packageQty,
        @NotNull IngredientUnit unit,
        @Min(0) @Max(99) int lossRate,
        @NotBlank String category
) {
}
