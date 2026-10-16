package com.tothework.store.store;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/onboarding")
@RequiredArgsConstructor
public class StoreOnboardingController {

    private final StoreOnboardingService storeOnboardingService;

    /** 온보딩 진행 상태 — OWNER 전용. */
    @GetMapping
    public ApiResponse<OnboardingResponse> getOnboarding(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeOnboardingService.getOnboarding(storeId, userId));
    }

    /** 단계 확인 처리 — OWNER 전용. 같은 단계를 다시 보내도 결과가 같다. */
    @PostMapping("/steps/{stepKey}")
    public ApiResponse<OnboardingResponse> confirmStep(@PathVariable Long storeId,
            @CurrentUserId Long userId, @PathVariable String stepKey) {
        return ApiResponse.success(storeOnboardingService.confirmStep(storeId, userId, stepKey));
    }

    /** 온보딩 마침 — OWNER 전용. 여러 번 눌러도 처음 끝낸 시각이 유지된다. */
    @PostMapping("/complete")
    public ApiResponse<OnboardingResponse> complete(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeOnboardingService.complete(storeId, userId));
    }
}
