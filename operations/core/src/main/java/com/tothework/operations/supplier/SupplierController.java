package com.tothework.operations.supplier;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    /** 거래처 등록 (OWNER/MANAGER). */
    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(supplierService.createSupplier(storeId, userId, request)));
    }

    /** 거래처 목록 (카테고리별 정렬) — OWNER/MANAGER 전용. */
    @GetMapping
    public ApiResponse<List<SupplierResponse>> getSuppliers(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(supplierService.getSuppliers(storeId, userId));
    }

    @PatchMapping("/{supplierId}")
    public ApiResponse<SupplierResponse> updateSupplier(@PathVariable Long storeId,
            @PathVariable Long supplierId, @CurrentUserId Long userId,
            @Valid @RequestBody SupplierRequest request) {
        return ApiResponse.success(supplierService.updateSupplier(storeId, supplierId, userId, request));
    }

    @DeleteMapping("/{supplierId}")
    public ApiResponse<Void> deleteSupplier(@PathVariable Long storeId, @PathVariable Long supplierId,
            @CurrentUserId Long userId) {
        supplierService.deleteSupplier(storeId, supplierId, userId);
        return ApiResponse.ok();
    }

    /** 발주 품목 등록 (OWNER/MANAGER). 요일별 발주량은 자유 텍스트. */
    @PostMapping("/{supplierId}/items")
    public ResponseEntity<ApiResponse<SupplierItemResponse>> addItem(@PathVariable Long storeId,
            @PathVariable Long supplierId, @CurrentUserId Long userId,
            @Valid @RequestBody SupplierItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(supplierService.addItem(storeId, supplierId, userId, request)));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<SupplierItemResponse> updateItem(@PathVariable Long storeId,
            @PathVariable Long itemId, @CurrentUserId Long userId,
            @Valid @RequestBody SupplierItemRequest request) {
        return ApiResponse.success(supplierService.updateItem(storeId, itemId, userId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> deleteItem(@PathVariable Long storeId, @PathVariable Long itemId,
            @CurrentUserId Long userId) {
        supplierService.deleteItem(storeId, itemId, userId);
        return ApiResponse.ok();
    }
}
