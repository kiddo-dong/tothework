package com.tothework.workforce.employment;

import com.tothework.store.api.MemberJoinedEvent;
import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** store의 이벤트를 받아 고용 조건을 만들고 지운다. store가 커밋한 뒤 별도 트랜잭션에서 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class StoreEventListeners {

    private final MemberEmploymentRepository repository;

    /**
     * 합류하면 기본 고용 조건(시급 0원, 공제 없음)을 만든다. 재합류는 예전 조건을 이어 쓴다 — 예전에도 같은 행을
     * 되살려 시급이 유지됐다. 이벤트가 두 번 와도(재전달) 이미 있으면 건드리지 않아 결과가 같다.
     */
    @ApplicationModuleListener
    void on(MemberJoinedEvent event) {
        if (!repository.existsById(event.memberId())) {
            repository.save(MemberEmployment.startFor(event.memberId(), event.storeId()));
        }
    }

    /** 완전히 지워진 매장의 고용 조건을 지운다. */
    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        repository.deleteAllOfStore(event.storeId());
    }
}
