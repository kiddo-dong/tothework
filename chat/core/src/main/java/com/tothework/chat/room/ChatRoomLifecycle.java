package com.tothework.chat.room;

import com.tothework.store.api.StoreCreatedEvent;
import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 매장의 생성·완전 삭제에 맞춰 채팅방을 만들고 지운다. */
@Component
@RequiredArgsConstructor
class ChatRoomLifecycle {

    private final ChatRooms chatRooms;

    /**
     * 매장 생성 트랜잭션이 커밋되기 직전, 같은 트랜잭션에서 방을 만든다. 커밋 뒤 비동기로 만들면 매장을 만들자마자 채팅을
     * 열 때 방이 아직 없을 수 있다. 방을 못 만들면 매장 생성도 함께 되돌아간다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void on(StoreCreatedEvent event) {
        chatRooms.createIfAbsent(event.storeId());
    }

    /** 완전히 지워진 매장의 방. 메시지는 ChatPurgeListener가 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        chatRooms.deleteOf(event.storeId());
    }
}
