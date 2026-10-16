package com.tothework.operations.notice;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 공지 확인 기록 (누가 언제 확인 버튼을 눌렀는지). 저장은 {@link NoticeReadRepository#insertIfAbsent}로만 한다. */
@Getter
@Table(schema = OperationsSchema.NAME, name = "notice_reads")
public class NoticeRead {

    @Id
    private final Long id;
    private final Long noticeId;
    private final Long memberId;
    private final LocalDateTime readAt;

    @PersistenceCreator
    NoticeRead(Long id, Long noticeId, Long memberId, LocalDateTime readAt) {
        this.id = id;
        this.noticeId = noticeId;
        this.memberId = memberId;
        this.readAt = readAt;
    }
}
