package com.tothework.operations.checklist;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 오픈/마감 체크리스트 항목 마스터. 한 번 등록하면 매일 반복된다. */
@Getter
@Table(schema = OperationsSchema.NAME, name = "checklist_items")
public class ChecklistItem {

    @Id
    private Long id;
    private final Long storeId;
    private ChecklistType type;
    private String content;
    private int displayOrder;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    ChecklistItem(Long id, Long storeId, ChecklistType type, String content, int displayOrder,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.type = type;
        this.content = content;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static ChecklistItem create(Long storeId, ChecklistType type, String content, int displayOrder) {
        return new ChecklistItem(null, storeId, type, content, displayOrder, null, null);
    }

    void update(ChecklistType type, String content, int displayOrder) {
        this.type = type;
        this.content = content;
        this.displayOrder = displayOrder;
    }
}
