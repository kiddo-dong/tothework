package com.tothework.store.store;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> createStore(@CurrentUserId Long userId,
            @Valid @RequestBody CreateStoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(storeService.createStore(userId, request)));
    }

    /** 내가 속한 매장 목록 (역할 무관, 재직 중인 매장만) + 각 매장에서의 내 역할. */
    @GetMapping
    public ApiResponse<List<MyStoreResponse>> getMyStores(@CurrentUserId Long userId) {
        return ApiResponse.success(storeService.getMyStores(userId));
    }

    /** 매장 대표 사진 등록·교체 — OWNER 전용. */
    @PostMapping("/{storeId}/profile-image")
    public ApiResponse<StoreResponse> updateProfileImage(@PathVariable Long storeId,
            @CurrentUserId Long userId, @RequestParam("image") MultipartFile image) {
        return ApiResponse.success(storeService.updateProfileImage(storeId, userId, image));
    }

    /** 매장 대표 사진 삭제 — OWNER 전용. */
    @DeleteMapping("/{storeId}/profile-image")
    public ApiResponse<StoreResponse> deleteProfileImage(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.deleteProfileImage(storeId, userId));
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StoreResponse> getStore(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.getStore(storeId, userId));
    }

    @PatchMapping("/{storeId}")
    public ApiResponse<StoreResponse> updateStore(@PathVariable Long storeId, @CurrentUserId Long userId,
            @Valid @RequestBody UpdateStoreRequest request) {
        return ApiResponse.success(storeService.updateStore(storeId, userId, request));
    }

    @DeleteMapping("/{storeId}")
    public ApiResponse<Void> deleteStore(@PathVariable Long storeId, @CurrentUserId Long userId,
            @RequestParam String confirmName) {
        storeService.deleteStore(storeId, userId, confirmName);
        return ApiResponse.ok();
    }

    @GetMapping("/{storeId}/invite-code")
    public ApiResponse<InviteCodeResponse> getInviteCode(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.getInviteCode(storeId, userId));
    }

    @PostMapping("/{storeId}/invite-code/regenerate")
    public ApiResponse<InviteCodeResponse> regenerateInviteCode(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeService.regenerateInviteCode(storeId, userId));
    }

    /** 소유권 이전: 다른 재직 멤버를 OWNER로 올리고 본인은 MANAGER로 내려간다 (OWNER 전용, 매장 이름 확인 필수). */
    @PostMapping("/{storeId}/transfer-ownership")
    public ApiResponse<Void> transferOwnership(@PathVariable Long storeId, @CurrentUserId Long userId,
            @Valid @RequestBody TransferOwnershipRequest request) {
        storeService.transferOwnership(storeId, userId, request);
        return ApiResponse.ok();
    }
}
