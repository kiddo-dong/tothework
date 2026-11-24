package com.tothework.chat.message;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 채팅 메시지를 지운다. 방은 ChatRoomLifecycle이 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class ChatPurgeListener {

    private final ChatMessageRepository repository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        repository.deleteAllOfStore(event.storeId());
    }
}
