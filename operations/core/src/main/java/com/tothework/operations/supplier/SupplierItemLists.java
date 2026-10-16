package com.tothework.operations.supplier;

import com.tothework.operations.config.OperationsSchema;
import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 거래처 여러 곳의 발주 품목을 요일별 발주량과 함께 한 번에 읽는다. {@link SupplierItem}을 저장소로 여러 개 읽으면
 * 품목마다 발주량 조회가 따라붙어 품목 수만큼 쿼리가 늘어난다. 품목 한 번·발주량 한 번으로 읽는다.
 */
@Component
@RequiredArgsConstructor
class SupplierItemLists {

    private static final String ITEMS = OperationsSchema.NAME + ".supplier_items";
    private static final String QUANTITIES = OperationsSchema.NAME + ".supplier_item_quantities";

    private final JdbcClient jdbcClient;

    /** supplierId → 품목 (표시 순서, id 순). 품목이 없는 거래처는 빠진다. */
    Map<Long, List<SupplierItemResponse>> ofSuppliers(Collection<Long> supplierIds) {
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<DayOfWeek, String>> quantities = new HashMap<>();
        jdbcClient.sql("SELECT q.item_id, q.day_of_week, q.quantity FROM " + QUANTITIES + " q JOIN " + ITEMS
                        + " i ON i.id = q.item_id WHERE i.supplier_id IN (:ids)")
                .param("ids", supplierIds)
                .query((rs, rowNum) -> quantities.computeIfAbsent(rs.getLong("item_id"),
                                id -> new EnumMap<>(DayOfWeek.class))
                        .put(DayOfWeek.valueOf(rs.getString("day_of_week")), rs.getString("quantity")))
                .list();
        record Row(long supplierId, SupplierItemResponse item) {
        }
        return jdbcClient.sql("SELECT id, supplier_id, name, spec, memo, display_order FROM " + ITEMS
                        + " WHERE supplier_id IN (:ids) ORDER BY display_order, id")
                .param("ids", supplierIds)
                .query((rs, rowNum) -> {
                    long id = rs.getLong("id");
                    return new Row(rs.getLong("supplier_id"), new SupplierItemResponse(id, rs.getString("name"),
                            rs.getString("spec"), rs.getString("memo"), rs.getInt("display_order"),
                            Map.copyOf(quantities.getOrDefault(id, Map.of()))));
                })
                .list().stream()
                .collect(Collectors.groupingBy(Row::supplierId, LinkedHashMap::new,
                        Collectors.mapping(Row::item, Collectors.toList())));
    }
}
