package com.tothework.chat.message;

import com.tothework.chat.config.ChatSchema;
import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 단체 채팅의 메시지 한 건. 매장 하나가 채팅방 하나라 {@code storeId}가 곧 방 id다.
 *
 * <p>수정·삭제 규칙(본인만, 삭제된 메시지는 불가)은 이 클래스가 검사한다. 삭제는 소프트 삭제라 목록에서 자리를
 * 지키고, 응답에서는 내용이 가려진다.
 *
 * @param senderId 보낸 사용자의 userId
 */
@Getter
@Table(schema = ChatSchema.NAME, name = "chat_messages")
public class ChatMessage {

    /** DB 컬럼 VARCHAR(500)과 같다. MySQL처럼 글자(코드 포인트) 수로 센다. */
    static final int MAX_LENGTH = 500;

    @Id
    private Long id;
    private final Long storeId;
    private final Long senderId;
    private String content;
    private boolean deleted;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    ChatMessage(Long id, Long storeId, Long senderId, String content, boolean deleted, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.senderId = senderId;
        this.content = content;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static ChatMessage write(Long storeId, Long senderId, String content) {
        return new ChatMessage(null, storeId, senderId, validContent(content), false, null, null);
    }

    void edit(Long userId, String newContent) {
        requireEditableBy(userId);
        this.content = validContent(newContent);
    }

    void delete(Long userId) {
        requireEditableBy(userId);
        this.deleted = true;
    }

    private void requireEditableBy(Long userId) {
        if (!senderId.equals(userId)) {
            throw new ForbiddenException("본인이 보낸 메시지만 수정하거나 삭제할 수 있습니다.");
        }
        if (deleted) {
            throw new ConflictException("이미 삭제된 메시지입니다.");
        }
    }

    /** 앞뒤 공백은 지운다. 공백만 있는 메시지는 보내지 않는다. */
    private static String validContent(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidRequestException("메시지 내용을 입력해 주세요.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
            throw new InvalidRequestException("메시지는 " + MAX_LENGTH + "자까지 보낼 수 있습니다.");
        }
        return trimmed;
    }
}
