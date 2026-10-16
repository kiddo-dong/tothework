package com.tothework.operations.checklist;

public record ChecklistItemResponse(
        Long id,
        ChecklistType type,
        String content,
        int displayOrder
) {

    static ChecklistItemResponse from(ChecklistItem item) {
        return new ChecklistItemResponse(item.getId(), item.getType(), item.getContent(), item.getDisplayOrder());
    }
}
