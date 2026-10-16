package com.tothework.operations.menu;

import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.StoreAccess;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴 원가 계산: 재료 단가와 레시피를 관리하고 재료비·원가율·이익을 계산한다. 원가는 관리자 전용. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuCostService {

    private final MenuIngredientRepository menuIngredientRepository;
    private final StoreMenuRepository storeMenuRepository;
    private final MenuLists menuLists;
    private final StoreAccess storeAccess;

    @Transactional
    public MenuIngredientResponse createIngredient(Long storeId, Long userId, MenuIngredientRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return MenuIngredientResponse.from(menuIngredientRepository.save(MenuIngredient.register(storeId, request)));
    }

    public List<MenuIngredientResponse> getIngredients(Long storeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return menuIngredientRepository.findAllByStoreIdOrderByCategoryAscIdAsc(storeId).stream()
                .map(MenuIngredientResponse::from)
                .toList();
    }

    @Transactional
    public MenuIngredientResponse updateIngredient(Long storeId, Long ingredientId, Long userId,
            MenuIngredientRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        MenuIngredient ingredient = getIngredientInStore(storeId, ingredientId);
        ingredient.update(request);
        return MenuIngredientResponse.from(menuIngredientRepository.save(ingredient));
    }

    /**
     * 재료 삭제 시 이 재료를 쓰는 레시피 줄도 함께 제거된다 (계산기와 동일 동작). 줄만 지우면 레시피 순서에 구멍이
     * 생기므로, 그 재료를 쓰는 메뉴를 애그리거트로 다시 저장한 뒤 재료를 지운다.
     */
    @Transactional
    public void deleteIngredient(Long storeId, Long ingredientId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        MenuIngredient ingredient = getIngredientInStore(storeId, ingredientId);
        for (StoreMenu menu : storeMenuRepository.findAllUsingIngredient(ingredient.getId())) {
            menu.removeIngredient(ingredient.getId());
            storeMenuRepository.save(menu);
        }
        menuIngredientRepository.delete(ingredient);
    }

    @Transactional
    public MenuResponse createMenu(Long storeId, Long userId, MenuRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Map<Long, MenuIngredient> ingredients = ingredientsOf(storeId, request.recipe());
        StoreMenu menu = storeMenuRepository.save(StoreMenu.create(storeId, request.name(), request.sellingPrice(),
                request.category(), toLines(request.recipe())));
        return MenuResponse.of(menu, ingredients);
    }

    public List<MenuResponse> getMenus(Long storeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        return menuLists.ofStore(storeId);
    }

    public MenuResponse getMenu(Long storeId, Long menuId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        StoreMenu menu = getMenuInStore(storeId, menuId);
        return MenuResponse.of(menu, ingredientsById(menu.getRecipe().stream().map(RecipeLine::ingredientId).toList()));
    }

    @Transactional
    public MenuResponse updateMenu(Long storeId, Long menuId, Long userId, MenuRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        StoreMenu menu = getMenuInStore(storeId, menuId);
        Map<Long, MenuIngredient> ingredients = ingredientsOf(storeId, request.recipe());
        menu.update(request.name(), request.sellingPrice(), request.category(), toLines(request.recipe()));
        return MenuResponse.of(storeMenuRepository.save(menu), ingredients);
    }

    @Transactional
    public void deleteMenu(Long storeId, Long menuId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        storeMenuRepository.delete(getMenuInStore(storeId, menuId));
    }

    /** 레시피가 쓰는 재료. 하나라도 이 매장 재료가 아니면 그 id로 404. */
    private Map<Long, MenuIngredient> ingredientsOf(Long storeId, List<MenuRequest.RecipeItemRequest> recipe) {
        if (recipe == null) {
            return Map.of();
        }
        Map<Long, MenuIngredient> found = ingredientsById(recipe.stream().map(MenuRequest.RecipeItemRequest::ingredientId)
                .distinct().toList());
        for (MenuRequest.RecipeItemRequest item : recipe) {
            MenuIngredient ingredient = found.get(item.ingredientId());
            if (ingredient == null || !ingredient.getStoreId().equals(storeId)) {
                throw new NotFoundException("재료를 찾을 수 없습니다: " + item.ingredientId());
            }
        }
        return found;
    }

    private Map<Long, MenuIngredient> ingredientsById(List<Long> ids) {
        return menuIngredientRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MenuIngredient::getId, Function.identity()));
    }

    private static List<RecipeLine> toLines(List<MenuRequest.RecipeItemRequest> recipe) {
        return recipe == null ? List.of()
                : recipe.stream().map(item -> new RecipeLine(item.ingredientId(), item.amount())).toList();
    }

    private MenuIngredient getIngredientInStore(Long storeId, Long ingredientId) {
        return menuIngredientRepository.findByIdAndStoreId(ingredientId, storeId)
                .orElseThrow(() -> new NotFoundException("재료를 찾을 수 없습니다: " + ingredientId));
    }

    private StoreMenu getMenuInStore(Long storeId, Long menuId) {
        return storeMenuRepository.findByIdAndStoreId(menuId, storeId)
                .orElseThrow(() -> new NotFoundException("메뉴를 찾을 수 없습니다."));
    }
}
