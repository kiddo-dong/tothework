package com.tothework.chat.room;

import com.tothework.chat.config.ChatSchema;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface ChatRoomRepository extends ListCrudRepository<ChatRoom, Long> {

    String TABLE = ChatSchema.NAME + ".chat_rooms";

    Optional<ChatRoom> findByStoreId(Long storeId);

    boolean existsByStoreId(Long storeId);

    /**
     * 없을 때만 만든다. 생성 이벤트와 기존 매장 채우기가 같은 매장을 두고 겹치거나, 두 서버가 동시에 채워도 방은 하나다.
     *
     * @return 새로 만들었으면 1
     */
    @Modifying
    @Query("INSERT IGNORE INTO " + TABLE + " (store_id, created_at) VALUES (:storeId, :createdAt)")
    int insertIfAbsent(Long storeId, LocalDateTime createdAt);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteByStoreId(Long storeId);
}
