package com.tothework.operations.handover;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 교대 인수인계 노트 ("우유 떨어짐, 6시 예약 있음"). 멤버 누구나 작성한다.
 *
 * @param authorId 작성한 멤버의 memberId
 */
@Getter
@Table(schema = OperationsSchema.NAME, name = "handover_notes")
public class HandoverNote {

    @Id
    private Long id;
    private final Long storeId;
    private final Long authorId;
    private String content;
    private LocalDate workDate;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    HandoverNote(Long id, Long storeId, Long authorId, String content, LocalDate workDate, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.authorId = authorId;
        this.content = content;
        this.workDate = workDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static HandoverNote write(Long storeId, Long authorId, String content, LocalDate workDate) {
        return new HandoverNote(null, storeId, authorId, content, workDate, null, null);
    }

    void update(String content, LocalDate workDate) {
        this.content = content;
        this.workDate = workDate;
    }
}
