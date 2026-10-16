package com.tothework.operations.menu;

import com.tothework.operations.config.OperationsSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 매장의 메뉴 전체를 원가와 함께 읽는다. {@link StoreMenu}를 저장소로 여러 개 읽으면 메뉴마다 레시피 조회가 따라붙는다.
 * 메뉴 한 번·레시피 줄 한 번·재료 한 번으로 읽는다.
 */
@Component
@RequiredArgsConstructor
class MenuLists {

    private static final String MENUS = OperationsSchema.NAME + ".store_menus";
    private static final String LINES = OperationsSchema.NAME + ".menu_recipe_items";

    private final JdbcClient jdbcClient;
    private final MenuIngredientRepository menuIngredientRepository;

    /** 카테고리·id 순. */
    List<MenuResponse> ofStore(Long storeId) {
        Map<Long, List<RecipeLine>> recipes = new HashMap<>();
        jdbcClient.sql("SELECT r.menu_id, r.ingredient_id, r.amount FROM " + LINES + " r JOIN " + MENUS
                        + " m ON m.id = r.menu_id WHERE m.store_id = :storeId ORDER BY r.menu_id, r.line_order")
                .param("storeId", storeId)
                .query((rs, rowNum) -> recipes.computeIfAbsent(rs.getLong("menu_id"), id -> new ArrayList<>())
                        .add(new RecipeLine(rs.getLong("ingredient_id"), rs.getDouble("amount"))))
                .list();
        Map<Long, MenuIngredient> ingredients = menuIngredientRepository.findAllByStoreId(storeId).stream()
                .collect(Collectors.toMap(MenuIngredient::getId, Function.identity()));
        return jdbcClient.sql("SELECT id, name, category, selling_price FROM " + MENUS
                        + " WHERE store_id = :storeId ORDER BY category, id")
                .param("storeId", storeId)
                .query((rs, rowNum) -> {
                    long id = rs.getLong("id");
                    return MenuResponse.of(id, rs.getString("name"), rs.getString("category"),
                            rs.getInt("selling_price"), recipes.getOrDefault(id, List.of()), ingredients);
                })
                .list();
    }
}
