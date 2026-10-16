package com.tothework.workforce.shift;

import com.tothework.shared.error.InvalidRequestException;
import com.tothework.workforce.config.WorkforceSchema;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장별 근무 유형 템플릿("오픈" 09:00~15:00 등). 스케줄을 만들 때 프론트가 시간을 복사해 넣는 프리셋이라, 템플릿을
 * 나중에 고쳐도 이미 만든 스케줄은 바뀌지 않는다.
 */
@Getter
@Table(schema = WorkforceSchema.NAME, name = "shift_templates")
public class ShiftTemplate {

    @Id
    private Long id;
    private final Long storeId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 휴게시간(분). null이면 스케줄 생성 시 매장 휴게 정책에 따라 자동 계산에 맡긴다. */
    private Integer breakMinutes;
    private int displayOrder;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    ShiftTemplate(Long id, Long storeId, String name, LocalTime startTime, LocalTime endTime, Integer breakMinutes,
            int displayOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static ShiftTemplate create(Long storeId, String name, LocalTime startTime, LocalTime endTime,
            Integer breakMinutes, int displayOrder) {
        validateTimeRange(startTime, endTime);
        return new ShiftTemplate(null, storeId, name, startTime, endTime, breakMinutes, displayOrder, null, null);
    }

    void update(String name, LocalTime startTime, LocalTime endTime, Integer breakMinutes, int displayOrder) {
        validateTimeRange(startTime, endTime);
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakMinutes = breakMinutes;
        this.displayOrder = displayOrder;
    }

    /** 자정을 넘는 야간 템플릿(예: 22:00~06:00) 여부. */
    public boolean isOvernight() {
        return endTime.isBefore(startTime);
    }

    private static void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime.equals(endTime)) {
            throw new InvalidRequestException("시작 시각과 종료 시각이 같을 수 없습니다.");
        }
    }
}
