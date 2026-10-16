package com.tothework.operations.menu;

import com.tothework.operations.config.OperationsSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface MenuIngredientRepository extends ListCrudRepository<MenuIngredient, Long> {

    List<MenuIngredient> findAllByStoreIdOrderByCategoryAscIdAsc(Long storeId);

    List<MenuIngredient> findAllByStoreId(Long storeId);

    Optional<MenuIngredient> findByIdAndStoreId(Long id, Long storeId);

    /** 메뉴를 먼저 지운 뒤에 불러야 한다. 레시피 줄이 재료를 가리키면 외래키가 막는다. */
    @Modifying
    @Query("DELETE FROM " + OperationsSchema.NAME + ".menu_ingredients WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
