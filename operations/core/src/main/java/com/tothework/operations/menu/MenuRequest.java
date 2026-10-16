package com.tothework.operations.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.Valid;
import java.util.List;

/** 메뉴 생성·수정 공용 요청. recipe는 전체 교체 방식이다. */
public record MenuRequest(
        @NotBlank String name,
        @PositiveOrZero int sellingPrice,
        @NotBlank String category,
        @Valid List<RecipeItemRequest> recipe
) {
    public record RecipeItemRequest(
            @NotNull Long ingredientId,
            @PositiveOrZero double amount
    ) {
    }
}
