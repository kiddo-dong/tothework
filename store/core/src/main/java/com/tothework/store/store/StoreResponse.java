package com.tothework.store.store;

import com.tothework.store.api.BreakPolicy;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.stream.Collectors;

public record StoreResponse(
        Long id,
        String name,
        String address,
        String businessRegistrationNumber,
        StoreCategory category,
        /** 매장 대표 사진 공개 URL. 등록하지 않았으면 null. */
        String profileImageUrl,
        Map<DayOfWeek, BusinessHourResponse> businessHours,
        BreakPolicy breakPolicy,
        boolean smallBusiness,
        /** 요금제. 아직 어떤 기능도 이 값에 따라 달라지지 않는다. */
        StorePlan plan,
        /** 급여 지급일(매월 며칠). 미지정이면 null. */
        Integer payday) {

    public record BusinessHourResponse(java.time.LocalTime openTime, java.time.LocalTime closeTime, boolean closed) {
    }

    static StoreResponse from(Store store, String profileImageUrl) {
        Map<DayOfWeek, BusinessHourResponse> businessHours = store.getBusinessHours().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new BusinessHourResponse(
                        entry.getValue().openTime(), entry.getValue().closeTime(), entry.getValue().closed())));
        return new StoreResponse(store.getId(), store.getName(), store.getAddress(),
                store.getBusinessRegistrationNumber(), store.getCategory(), profileImageUrl, businessHours,
                store.getBreakPolicy(), store.isSmallBusiness(), store.getPlan(), store.getPayday());
    }
}
