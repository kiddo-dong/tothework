package com.tothework.store.store;

import com.tothework.store.config.StoreSchema;
import org.springframework.data.relational.core.mapping.Table;

/** 사장님이 확인했다고 표시한 온보딩 단계. 단계 이름은 화면이 정한다. */
@Table(schema = StoreSchema.NAME, name = "store_onboarding_steps")
record OnboardingStep(String stepKey) {
}
