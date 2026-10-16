package com.tothework.operations.menu;

import java.util.List;
import java.util.Map;

/** 메뉴 + 계산된 원가 지표. 재료비 = Σ(단위단가 × 사용량), 이익 = 판매가 - 재료비. */
public record MenuResponse(
        Long id,
        String name,
        String category,
        int sellingPrice,
        long ingredientCost,
        double ingredientCostRate,
        long profit,
        double profitRate,
        List<RecipeLineResponse> recipe
) {

    public record RecipeLineResponse(
            Long ingredientId,
            String ingredientName,
            IngredientUnit unit,
            double unitCost,
            double amount,
            long lineCost
    ) {
    }

    /** @param ingredients 레시피가 쓰는 재료 (id → 재료). 같은 매장의 재료만 레시피에 들어가므로 모두 있다 */
    static MenuResponse of(StoreMenu menu, Map<Long, MenuIngredient> ingredients) {
        return of(menu.getId(), menu.getName(), menu.getCategory(), menu.getSellingPrice(), menu.getRecipe(),
                ingredients);
    }

    static MenuResponse of(Long id, String name, String category, int sellingPrice, List<RecipeLine> recipe,
            Map<Long, MenuIngredient> ingredients) {
        List<RecipeLineResponse> lines = recipe.stream()
                .map(line -> {
                    MenuIngredient ingredient = ingredients.get(line.ingredientId());
                    return new RecipeLineResponse(ingredient.getId(), ingredient.getName(), ingredient.getUnit(),
                            ingredient.unitCost(), line.amount(), Math.round(ingredient.unitCost() * line.amount()));
                })
                .toList();
        double rawCost = recipe.stream()
                .mapToDouble(line -> ingredients.get(line.ingredientId()).unitCost() * line.amount())
                .sum();
        long ingredientCost = Math.round(rawCost);
        long profit = sellingPrice - ingredientCost;
        double costRate = sellingPrice > 0 ? rawCost * 100.0 / sellingPrice : 0;
        double profitRate = sellingPrice > 0 ? profit * 100.0 / sellingPrice : 0;
        return new MenuResponse(id, name, category, sellingPrice, ingredientCost,
                costRate, profit, profitRate, lines);
    }
}
