package com.tothework.operations.supplier;

import com.tothework.operations.config.OperationsSchema;
import org.springframework.data.relational.core.mapping.Table;

/** 한 요일의 발주량 (자유 텍스트, 예: "2박스"). 요일은 {@link SupplierItem}의 맵 키다. */
@Table(schema = OperationsSchema.NAME, name = "supplier_item_quantities")
record DailyQuantity(String quantity) {
}
