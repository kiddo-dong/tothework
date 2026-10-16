package com.tothework.operations.checklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChecklistItemRequest(
        @NotNull ChecklistType type,
        @NotBlank String content,
        Integer displayOrder
) {
    public int displayOrderOrDefault() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
