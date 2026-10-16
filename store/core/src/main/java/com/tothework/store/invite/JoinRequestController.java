package com.tothework.store.invite;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JoinRequestController {

    private final JoinRequestService joinRequestService;

    @PostMapping("/api/v1/join-requests")
    public ResponseEntity<ApiResponse<JoinRequestResponse>> requestJoin(@CurrentUserId Long userId,
            @Valid @RequestBody JoinRequestByCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(joinRequestService.requestJoin(userId, request.code())));
    }

    @GetMapping("/api/v1/join-requests/me")
    public ApiResponse<List<JoinRequestResponse>> getMyRequests(@CurrentUserId Long userId) {
        return ApiResponse.success(joinRequestService.getMyRequests(userId));
    }

    @DeleteMapping("/api/v1/join-requests/{requestId}")
    public ApiResponse<Void> cancelMyRequest(@PathVariable Long requestId, @CurrentUserId Long userId) {
        joinRequestService.cancelMyRequest(requestId, userId);
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/stores/{storeId}/join-requests")
    public ApiResponse<List<JoinRequestResponse>> getPendingRequests(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(joinRequestService.getPendingRequests(storeId, userId));
    }

    @PostMapping("/api/v1/stores/{storeId}/join-requests/{requestId}/approve")
    public ApiResponse<JoinRequestResponse> approve(@PathVariable Long storeId, @PathVariable Long requestId,
            @CurrentUserId Long userId, @Valid @RequestBody ApproveJoinRequestRequest request) {
        return ApiResponse.success(joinRequestService.approve(storeId, requestId, userId, request));
    }

    @PostMapping("/api/v1/stores/{storeId}/join-requests/{requestId}/reject")
    public ApiResponse<JoinRequestResponse> reject(@PathVariable Long storeId, @PathVariable Long requestId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(joinRequestService.reject(storeId, requestId, userId));
    }
}
