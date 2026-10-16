package com.tothework.operations.notice;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 공지사항. 관리자가 작성하고 멤버가 확인 버튼으로 읽음을 남긴다.
 *
 * @param authorId 작성한 멤버의 memberId
 */
@Getter
@Table(schema = OperationsSchema.NAME, name = "notices")
public class Notice {

    @Id
    private Long id;
    private final Long storeId;
    private final Long authorId;
    private final String title;
    private final String content;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    Notice(Long id, Long storeId, Long authorId, String title, String content, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static Notice write(Long storeId, Long authorId, String title, String content) {
        return new Notice(null, storeId, authorId, title, content, null, null);
    }
}
