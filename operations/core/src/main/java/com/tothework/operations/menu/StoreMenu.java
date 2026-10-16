package com.tothework.operations.menu;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** 판매 메뉴와 레시피(재료 사용량). 재료비·원가율·이익 계산의 단위가 된다. 레시피는 늘 통째로 교체된다. */
@Getter
@Table(schema = OperationsSchema.NAME, name = "store_menus")
public class StoreMenu {

    @Id
    private Long id;
    private final Long storeId;
    private String name;
    /** 판매가 (원). */
    private int sellingPrice;
    /** 자유 카테고리 (커피, 디저트, 식사, 사이드 등 매장이 직접 정의). */
    private String category;
    @MappedCollection(idColumn = "menu_id", keyColumn = "line_order")
    private List<RecipeLine> recipe = new ArrayList<>();
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    StoreMenu(Long id, Long storeId, String name, int sellingPrice, String category, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static StoreMenu create(Long storeId, String name, int sellingPrice, String category, List<RecipeLine> recipe) {
        StoreMenu menu = new StoreMenu(null, storeId, name, sellingPrice, category, null, null);
        menu.replaceRecipe(recipe);
        return menu;
    }

    void update(String name, int sellingPrice, String category, List<RecipeLine> recipe) {
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.category = category;
        replaceRecipe(recipe);
    }

    /** 재료가 지워질 때 그 재료를 쓰는 줄을 뺀다. 남은 줄은 순서를 유지한 채 다시 번호가 매겨진다. */
    void removeIngredient(Long ingredientId) {
        replaceRecipe(recipe.stream().filter(line -> !line.ingredientId().equals(ingredientId)).toList());
    }

    private void replaceRecipe(List<RecipeLine> lines) {
        this.recipe = new ArrayList<>(lines);
    }
}
