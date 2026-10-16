package com.tothework.workforce.payroll;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 급여 기록을 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class PayrollPurgeListener {

    private final PayrollRecords payrollRecords;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        payrollRecords.deleteAllOfStore(event.storeId());
    }
}
