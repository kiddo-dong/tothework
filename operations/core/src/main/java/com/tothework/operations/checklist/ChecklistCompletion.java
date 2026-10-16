package com.tothework.operations.checklist;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 날짜별 체크 기록 (항목당 하루 1건). 누가 언제 체크했는지 남는다. 저장은 {@link ChecklistCompletionRepository#insertIfAbsent}
 * 한 문장으로만 하므로 생성 메서드가 없다.
 *
 * @param checkedBy 체크한 멤버의 memberId
 */
@Getter
@Table(schema = OperationsSchema.NAME, name = "checklist_completions")
public class ChecklistCompletion {

    @Id
    private final Long id;
    private final Long itemId;
    private final Long storeId;
    private final LocalDate workDate;
    private final Long checkedBy;
    private final LocalDateTime checkedAt;

    @PersistenceCreator
    ChecklistCompletion(Long id, Long itemId, Long storeId, LocalDate workDate, Long checkedBy,
            LocalDateTime checkedAt) {
        this.id = id;
        this.itemId = itemId;
        this.storeId = storeId;
        this.workDate = workDate;
        this.checkedBy = checkedBy;
        this.checkedAt = checkedAt;
    }
}
