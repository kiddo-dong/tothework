package com.tothework.operations.menu;

import com.tothework.operations.config.OperationsSchema;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 레시피 한 줄: 어떤 재료를 메뉴 1인분에 얼마나 쓰는지. 재료는 별도 애그리거트라 id로 가리킨다.
 *
 * @param amount 1인분 사용량 (재료의 unit 기준)
 */
@Table(schema = OperationsSchema.NAME, name = "menu_recipe_items")
record RecipeLine(Long ingredientId, double amount) {
}
