package com.tothework.operations.notice;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 공지를 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class NoticePurgeListener {

    private final NoticeRepository noticeRepository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        noticeRepository.deleteAllOfStore(event.storeId());
    }
}
