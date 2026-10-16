package com.tothework.operations.menu;

import com.tothework.store.api.StorePurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 완전히 지워진 매장의 메뉴와 재료를 지운다. store의 삭제가 커밋된 뒤 비동기로 실행된다. 레시피 줄이 재료를 가리키므로
 * 메뉴(와 CASCADE로 레시피 줄)를 먼저 지운다.
 */
@Component
@RequiredArgsConstructor
class MenuPurgeListener {

    private final StoreMenuRepository storeMenuRepository;
    private final MenuIngredientRepository menuIngredientRepository;

    @ApplicationModuleListener
    void on(StorePurgedEvent event) {
        storeMenuRepository.deleteAllOfStore(event.storeId());
        menuIngredientRepository.deleteAllOfStore(event.storeId());
    }
}
