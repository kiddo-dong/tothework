package com.tothework.laborqa.conversation;

import com.tothework.laborqa.config.LaborQaSchema;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 대화 세션 안의 메시지 한 건 (질문 또는 답변). 세션은 id로 참조한다. */
@Getter
@Table(schema = LaborQaSchema.NAME, name = "labor_qa_messages")
public class LaborQaMessage {

    private static final String SOURCE_DELIMITER = "\n";

    @Id
    private Long id;
    private final Long sessionId;
    private final LaborQaRole role;
    private final String content;
    /** 답변의 참고 출처 목록. 출처 제목에 개행이 없으므로 개행으로 이어 붙여 한 컬럼에 저장한다. */
    private final String sources;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    LaborQaMessage(Long id, Long sessionId, LaborQaRole role, String content, String sources,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.sources = sources;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static LaborQaMessage question(Long sessionId, String content) {
        return new LaborQaMessage(null, sessionId, LaborQaRole.USER, content, null, null, null);
    }

    static LaborQaMessage answer(Long sessionId, String content, List<String> sources) {
        String joined = sources == null || sources.isEmpty() ? null : String.join(SOURCE_DELIMITER, sources);
        return new LaborQaMessage(null, sessionId, LaborQaRole.ASSISTANT, content, joined, null, null);
    }

    public List<String> getSourceList() {
        return sources == null ? List.of() : Arrays.asList(sources.split(SOURCE_DELIMITER));
    }
}
