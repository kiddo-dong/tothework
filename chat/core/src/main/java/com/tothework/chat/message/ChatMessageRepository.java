package com.tothework.chat.message;

import com.tothework.chat.config.ChatSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface ChatMessageRepository extends ListCrudRepository<ChatMessage, Long> {

    String TABLE = ChatSchema.NAME + ".chat_messages";

    /** 다른 매장의 메시지 id를 넣어도 찾지 못한다. 수정·삭제는 반드시 이걸로 읽는다. */
    Optional<ChatMessage> findByIdAndStoreId(Long id, Long storeId);

    /** 최신 메시지부터. (store_id, id) 인덱스로 읽는다. */
    List<ChatMessage> findByStoreIdOrderByIdDesc(Long storeId, Limit limit);

    /** {@code before}보다 오래된 메시지부터. 커서 페이지네이션의 다음 페이지. */
    List<ChatMessage> findByStoreIdAndIdLessThanOrderByIdDesc(Long storeId, Long before, Limit limit);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
