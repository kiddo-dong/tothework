package com.tothework.operations.menu;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 메뉴 원가 계산기 API. 원가·이익은 민감정보라 전부 OWNER/MANAGER 전용이다. */
@RestController
@RequiredArgsConstructor
public class MenuCostController {

    private final MenuCostService menuCostService;

    // ── 재료 단가 ─────────────────────────────────────────────

    @PostMapping("/api/v1/stores/{storeId}/menu-ingredients")
    public ResponseEntity<ApiResponse<MenuIngredientResponse>> createIngredient(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody MenuIngredientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(menuCostService.createIngredient(storeId, userId, request)));
    }

    @GetMapping("/api/v1/stores/{storeId}/menu-ingredients")
    public ApiResponse<List<MenuIngredientResponse>> getIngredients(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(menuCostService.getIngredients(storeId, userId));
    }

    @PatchMapping("/api/v1/stores/{storeId}/menu-ingredients/{ingredientId}")
    public ApiResponse<MenuIngredientResponse> updateIngredient(@PathVariable Long storeId,
            @PathVariable Long ingredientId, @CurrentUserId Long userId,
            @Valid @RequestBody MenuIngredientRequest request) {
        return ApiResponse.success(
                menuCostService.updateIngredient(storeId, ingredientId, userId, request));
    }

    @DeleteMapping("/api/v1/stores/{storeId}/menu-ingredients/{ingredientId}")
    public ApiResponse<Void> deleteIngredient(@PathVariable Long storeId, @PathVariable Long ingredientId,
            @CurrentUserId Long userId) {
        menuCostService.deleteIngredient(storeId, ingredientId, userId);
        return ApiResponse.ok();
    }

    // ── 메뉴 (레시피 + 원가 계산) ─────────────────────────────

    @PostMapping("/api/v1/stores/{storeId}/menus")
    public ResponseEntity<ApiResponse<MenuResponse>> createMenu(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody MenuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(menuCostService.createMenu(storeId, userId, request)));
    }

    @GetMapping("/api/v1/stores/{storeId}/menus")
    public ApiResponse<List<MenuResponse>> getMenus(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(menuCostService.getMenus(storeId, userId));
    }

    @GetMapping("/api/v1/stores/{storeId}/menus/{menuId}")
    public ApiResponse<MenuResponse> getMenu(@PathVariable Long storeId, @PathVariable Long menuId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(menuCostService.getMenu(storeId, menuId, userId));
    }

    @PatchMapping("/api/v1/stores/{storeId}/menus/{menuId}")
    public ApiResponse<MenuResponse> updateMenu(@PathVariable Long storeId, @PathVariable Long menuId,
            @CurrentUserId Long userId, @Valid @RequestBody MenuRequest request) {
        return ApiResponse.success(menuCostService.updateMenu(storeId, menuId, userId, request));
    }

    @DeleteMapping("/api/v1/stores/{storeId}/menus/{menuId}")
    public ApiResponse<Void> deleteMenu(@PathVariable Long storeId, @PathVariable Long menuId,
            @CurrentUserId Long userId) {
        menuCostService.deleteMenu(storeId, menuId, userId);
        return ApiResponse.ok();
    }
}
