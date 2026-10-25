package com.tothework.operations.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 요일별 발주량. 수량이 빈 요일은 발주 없는 요일로 본다. */
class SupplierItemTest {

    /** {"MONDAY": null}을 보내면 응답을 만들다 Map.copyOf가 NPE를 내 500이 났다. */
    @Test
    void anEmptyQuantityMeansNoOrderThatDay() {
        Map<DayOfWeek, String> weekly = new HashMap<>();
        weekly.put(DayOfWeek.MONDAY, null);
        weekly.put(DayOfWeek.TUESDAY, " ");
        weekly.put(DayOfWeek.THURSDAY, "2박스");

        SupplierItem item = SupplierItem.add(1L, new SupplierItemRequest("우유", "1L", null, null, weekly));
        SupplierItemResponse response = SupplierItemResponse.from(item);

        assertThat(response.weeklyQuantities()).containsExactly(Map.entry(DayOfWeek.THURSDAY, "2박스"));
    }

    @Test
    void updatingWithAnEmptyQuantityRemovesThatDay() {
        SupplierItem item = SupplierItem.add(1L, new SupplierItemRequest("우유", null, null, null,
                Map.of(DayOfWeek.MONDAY, "10개", DayOfWeek.FRIDAY, "5개")));
        Map<DayOfWeek, String> changed = new HashMap<>();
        changed.put(DayOfWeek.MONDAY, null);
        changed.put(DayOfWeek.FRIDAY, "5개");

        item.update(new SupplierItemRequest("우유", null, null, null, changed));

        assertThat(SupplierItemResponse.from(item).weeklyQuantities())
                .containsExactly(Map.entry(DayOfWeek.FRIDAY, "5개"));
    }
}
