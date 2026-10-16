package com.tothework.laborqa.conversation;

import com.tothework.laborqa.config.LaborQaSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 근로 상담 대화 세션 (애그리거트 루트). 매장이 아니라 사용자 소유의 데이터다.
 *
 * <p>소유자는 id로만 기억한다. 사용자는 identity 서비스의 것이라 객체로 들고 있을 수 없다.
 *
 * <p>메시지는 이 애그리거트에 넣지 않았다. Spring Data JDBC는 애그리거트를 저장할 때 자식 전체를 지우고 다시
 * 넣는데, 대화가 길어질수록 질문 하나 저장에 수백 행을 다시 쓰게 된다. 그래서 메시지는 세션 id를 참조하는 별도
 * 애그리거트다.
 */
@Getter
@Table(schema = LaborQaSchema.NAME, name = "labor_qa_sessions")
public class LaborQaSession {

    private static final int TITLE_MAX_LENGTH = 30;

    @Id
    private Long id;
    private final Long userId;
    /** 목록 표시용 제목. 첫 질문에서 잘라 자동 생성된다. */
    private String title;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    LaborQaSession(Long id, Long userId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static LaborQaSession startFor(Long userId) {
        return new LaborQaSession(null, userId, null, null, null);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 첫 질문이 들어올 때 한 번만 제목을 채운다. 저장은 호출한 쪽이 명시적으로 한다. */
    void fillTitleIfEmpty(String question) {
        if (title == null) {
            title = question.length() > TITLE_MAX_LENGTH ? question.substring(0, TITLE_MAX_LENGTH) : question;
        }
    }
}
