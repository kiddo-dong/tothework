package com.tothework.operations.handover;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 인수인계 노트를 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class HandoverPurgeListener {

    private final HandoverNoteRepository handoverNoteRepository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        handoverNoteRepository.deleteAllOfStore(event.storeId());
    }
}
