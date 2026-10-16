package com.tothework.store.store;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매장 온보딩 진행 상태. 어떤 단계가 있는지는 화면이 정하므로, 사장님이 확인한 단계와 마쳤는지만 담는다.
 *
 * @param confirmedSteps 사장님이 확인한 단계 이름들. 순서는 의미 없다
 * @param completedAt    온보딩을 마쳤다고 표시한 시각. 아직이면 null
 */
public record OnboardingResponse(List<String> confirmedSteps, LocalDateTime completedAt, boolean completed) {

    static OnboardingResponse from(Store store) {
        return new OnboardingResponse(List.copyOf(store.getConfirmedOnboardingSteps()),
                store.getOnboardingCompletedAt(), store.getOnboardingCompletedAt() != null);
    }
}
