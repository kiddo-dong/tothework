package com.tothework.workforce.shift.template;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 완전히 지워진 매장의 근무 유형 템플릿을 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. */
@Component
@RequiredArgsConstructor
class ShiftTemplatePurgeListener {

    private final ShiftTemplateRepository repository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        repository.deleteAllOfStore(event.storeId());
    }
}
