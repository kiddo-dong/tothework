package com.tothework.workforce.attendance;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 근태 기록을 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class AttendancePurgeListener {

    private final AttendanceRepository attendanceRepository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        attendanceRepository.deleteAllOfStore(event.storeId());
    }
}
