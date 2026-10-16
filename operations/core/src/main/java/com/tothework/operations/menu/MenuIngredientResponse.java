package com.tothework.operations.menu;

public record MenuIngredientResponse(
        Long id,
        String name,
        String productInfo,
        int price,
        double packageQty,
        IngredientUnit unit,
        int lossRate,
        String category,
        double unitCost
) {

    static MenuIngredientResponse from(MenuIngredient ingredient) {
        return new MenuIngredientResponse(ingredient.getId(), ingredient.getName(), ingredient.getProductInfo(),
                ingredient.getPrice(), ingredient.getPackageQty(), ingredient.getUnit(), ingredient.getLossRate(),
                ingredient.getCategory(), ingredient.unitCost());
    }
}
