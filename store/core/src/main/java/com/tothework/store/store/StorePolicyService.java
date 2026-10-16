package com.tothework.store.store;

import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.StorePolicies;
import com.tothework.store.api.StorePolicy;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class StorePolicyService implements StorePolicies {

    private final StoreRepository storeRepository;

    @Override
    public StorePolicy policyOf(Long storeId) {
        Store store = storeRepository.findActive(storeId).orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다."));
        Map<java.time.DayOfWeek, StorePolicy.Hours> hours = store.getBusinessHours().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new StorePolicy.Hours(
                        entry.getValue().openTime(), entry.getValue().closeTime(), entry.getValue().closed())));
        return new StorePolicy(store.getId(), store.isSmallBusiness(), store.getBreakPolicy(), Map.copyOf(hours),
                store.getPayday());
    }
}
