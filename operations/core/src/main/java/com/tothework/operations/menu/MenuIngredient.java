package com.tothework.operations.menu;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/** 메뉴 원가 계산용 재료 단가. 구매가와 구매 용량으로 단위당 단가를 계산한다 (로스율 반영). */
@Getter
@Table(schema = OperationsSchema.NAME, name = "menu_ingredients")
public class MenuIngredient {

    @Id
    private Long id;
    private final Long storeId;
    private String name;
    /** 구매 제품 정보 (예: "SizeUp · 과일프로젝트 딸기청 1.4kg", "곰곰 냉동 삼겹살 1kg"). */
    private String productInfo;
    /** 구매가 (원). */
    private int price;
    /** 구매 용량 (unit 기준). */
    private double packageQty;
    private IngredientUnit unit;
    /** 로스율 % (0~99). 손질·증발 등으로 실제 사용 가능한 양이 줄어드는 비율. */
    private int lossRate;
    /** 자유 카테고리 (원두, 유제품, 육류, 채소 등 매장이 직접 정의). */
    private String category;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    MenuIngredient(Long id, Long storeId, String name, String productInfo, int price, double packageQty,
            IngredientUnit unit, int lossRate, String category, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.name = name;
        this.productInfo = productInfo;
        this.price = price;
        this.packageQty = packageQty;
        this.unit = unit;
        this.lossRate = lossRate;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static MenuIngredient register(Long storeId, MenuIngredientRequest request) {
        MenuIngredient ingredient = new MenuIngredient(null, storeId, null, null, 0, 0, null, 0, null, null, null);
        ingredient.update(request);
        return ingredient;
    }

    void update(MenuIngredientRequest request) {
        this.name = request.name();
        this.productInfo = request.productInfo();
        this.price = request.price();
        this.packageQty = request.packageQty();
        this.unit = request.unit();
        this.lossRate = request.lossRate();
        this.category = request.category();
    }

    /** 단위당 단가 = 구매가 ÷ (구매 용량 × (1 - 로스율)). 사용 가능량이 0 이하이면 0. */
    public double unitCost() {
        double usableQty = packageQty * (1 - lossRate / 100.0);
        return usableQty <= 0 ? 0 : price / usableQty;
    }
}
