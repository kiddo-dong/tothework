package com.tothework.workforce.leave;

import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 연차 사용 기록. 하루 단위로 기록하며 같은 날짜에 중복 사용할 수 없다(유니크 키). */
@Getter
@Table(schema = WorkforceSchema.NAME, name = "leave_usages")
public class LeaveUsage {

    @Id
    private Long id;
    private final Long memberId;
    private final Long storeId;
    private final LocalDate leaveDate;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    LeaveUsage(Long id, Long memberId, Long storeId, LocalDate leaveDate, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.storeId = storeId;
        this.leaveDate = leaveDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static LeaveUsage on(Long memberId, Long storeId, LocalDate leaveDate) {
        return new LeaveUsage(null, memberId, storeId, leaveDate, null, null);
    }
}
