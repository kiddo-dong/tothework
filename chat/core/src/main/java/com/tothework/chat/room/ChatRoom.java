package com.tothework.chat.room;

import com.tothework.chat.config.ChatSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 매장 채팅방. 지금은 매장 하나에 방 하나다. 메시지는 매장 id({@code store_id})로 이 방에 속한다.
 *
 * <p>방에 딸린 설정(이름, 알림 등)이나 매장 안의 여러 방이 생기면 여기서 늘린다.
 */
@Getter
@Table(schema = ChatSchema.NAME, name = "chat_rooms")
public class ChatRoom {

    @Id
    private final Long id;
    private final Long storeId;
    private final LocalDateTime createdAt;

    @PersistenceCreator
    ChatRoom(Long id, Long storeId, LocalDateTime createdAt) {
        this.id = id;
        this.storeId = storeId;
        this.createdAt = createdAt;
    }
}
