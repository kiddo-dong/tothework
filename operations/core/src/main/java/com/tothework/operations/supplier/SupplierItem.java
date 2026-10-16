package com.tothework.operations.supplier;

import com.tothework.operations.config.OperationsSchema;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 발주 품목: 이 거래처에서 정기적으로 시키는 물건과 요일별 발주량. 신입도 "무슨 요일에 뭘 얼마나 시키는지" 목록만
 * 보고 발주할 수 있게 하는 지식 데이터다. 수량은 "2박스", "10개"처럼 단위가 제각각이라 자유 텍스트로 둔다.
 */
@Getter
@Table(schema = OperationsSchema.NAME, name = "supplier_items")
public class SupplierItem {

    @Id
    private Long id;
    private final Long supplierId;
    private String name;
    /** 규격/단위 설명 (예: "1L × 12개입", "1박스=100장"). */
    private String spec;
    private String memo;
    private int displayOrder;
    /** 요일별 발주량. 발주 없는 요일은 키 자체가 없다. 늘 통째로 교체되므로 자식 컬렉션이다. */
    @MappedCollection(idColumn = "item_id", keyColumn = "day_of_week")
    private Map<DayOfWeek, DailyQuantity> quantities = new HashMap<>();
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PersistenceCreator
    SupplierItem(Long id, Long supplierId, String name, String spec, String memo, int displayOrder,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.supplierId = supplierId;
        this.name = name;
        this.spec = spec;
        this.memo = memo;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static SupplierItem add(Long supplierId, SupplierItemRequest request) {
        SupplierItem item = new SupplierItem(null, supplierId, null, null, null, 0, null, null);
        item.update(request);
        return item;
    }

    void update(SupplierItemRequest request) {
        this.name = request.name();
        this.spec = request.spec();
        this.memo = request.memo();
        this.displayOrder = request.displayOrderOrDefault();
        this.quantities = request.weeklyQuantities() == null ? new HashMap<>()
                : request.weeklyQuantities().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new DailyQuantity(entry.getValue()), (a, b) -> a, HashMap::new));
    }

    Map<DayOfWeek, String> weeklyQuantities() {
        Map<DayOfWeek, String> weekly = new EnumMap<>(DayOfWeek.class);
        quantities.forEach((day, quantity) -> weekly.put(day, quantity.quantity()));
        return weekly;
    }
}
