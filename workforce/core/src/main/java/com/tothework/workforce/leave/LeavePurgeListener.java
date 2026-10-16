package com.tothework.workforce.leave;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 연차 사용 기록을 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class LeavePurgeListener {

    private final LeaveUsageRepository leaveUsageRepository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        leaveUsageRepository.deleteAllOfStore(event.storeId());
    }
}
