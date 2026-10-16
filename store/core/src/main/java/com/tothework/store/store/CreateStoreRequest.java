package com.tothework.store.store;

import com.tothework.store.api.BreakPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.DayOfWeek;
import java.util.Map;

public record CreateStoreRequest(
        @NotBlank String name,
        String address,
        String businessRegistrationNumber,
        StoreCategory category,
        Map<DayOfWeek, BusinessHourRequest> businessHours,
        BreakPolicy breakPolicy,
        Boolean smallBusiness,
        /** 급여 지급일(매월 며칠). 안 정했으면 비워둔다. */
        @Min(value = 1, message = "급여일은 1~31 사이여야 합니다.")
        @Max(value = 31, message = "급여일은 1~31 사이여야 합니다.")
        Integer payday
) {
}
