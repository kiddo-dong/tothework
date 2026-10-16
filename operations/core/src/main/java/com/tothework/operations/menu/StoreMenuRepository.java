package com.tothework.operations.menu;

import com.tothework.operations.config.OperationsSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface StoreMenuRepository extends ListCrudRepository<StoreMenu, Long> {

    String TABLE = OperationsSchema.NAME + ".store_menus";

    Optional<StoreMenu> findByIdAndStoreId(Long id, Long storeId);

    /** 이 재료를 레시피에 쓰는 메뉴. 재료 삭제 때 레시피를 다시 저장하려고 찾는다. */
    @Query("SELECT DISTINCT m.* FROM " + TABLE + " m JOIN " + OperationsSchema.NAME
            + ".menu_recipe_items r ON r.menu_id = m.id WHERE r.ingredient_id = :ingredientId")
    List<StoreMenu> findAllUsingIngredient(Long ingredientId);

    /** 레시피 줄은 같은 스키마의 CASCADE로 함께 지워진다. */
    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE store_id = :storeId")
    void deleteAllOfStore(Long storeId);
}
