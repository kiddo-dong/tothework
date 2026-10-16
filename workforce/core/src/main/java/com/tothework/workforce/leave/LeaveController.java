package com.tothework.workforce.leave;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping("/api/v1/stores/{storeId}/members/{memberId}/leaves")
    public ApiResponse<LeaveStatusResponse> getLeaveStatus(@PathVariable Long storeId,
            @PathVariable Long memberId, @CurrentUserId Long userId) {
        return ApiResponse.success(leaveService.getLeaveStatus(storeId, memberId, userId));
    }

    @PostMapping("/api/v1/stores/{storeId}/members/{memberId}/leaves")
    public ResponseEntity<ApiResponse<LeaveUsageResponse>> useLeave(@PathVariable Long storeId,
            @PathVariable Long memberId, @CurrentUserId Long userId,
            @Valid @RequestBody UseLeaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(leaveService.useLeave(storeId, memberId, userId, request)));
    }

    @DeleteMapping("/api/v1/stores/{storeId}/leaves/{leaveId}")
    public ApiResponse<Void> cancelLeave(@PathVariable Long storeId, @PathVariable Long leaveId,
            @CurrentUserId Long userId) {
        leaveService.cancelLeave(storeId, leaveId, userId);
        return ApiResponse.ok();
    }
}
