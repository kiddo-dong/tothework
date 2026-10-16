package com.tothework.operations.supplier;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 거래처를 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class SupplierPurgeListener {

    private final SupplierRepository supplierRepository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        supplierRepository.deleteAllOfStore(event.storeId());
    }
}
