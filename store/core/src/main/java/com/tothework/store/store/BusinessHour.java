package com.tothework.store.store;

import com.tothework.store.config.StoreSchema;
import java.time.LocalTime;
import org.springframework.data.relational.core.mapping.Table;

/** 요일 하나의 영업시간. 매장 애그리거트의 일부라 매장과 함께 저장된다. */
@Table(schema = StoreSchema.NAME, name = "store_business_hours")
public record BusinessHour(LocalTime openTime, LocalTime closeTime, boolean closed) {
}
