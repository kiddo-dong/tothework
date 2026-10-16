package com.tothework.store.store;

import com.tothework.store.api.BreakPolicy;
import com.tothework.store.config.StoreSchema;
import com.tothework.store.store.StoreResponse.BusinessHourResponse;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 매장 여러 개를 화면용 응답으로 한 번에 읽는다.
 *
 * <p>Spring Data JDBC는 애그리거트를 읽을 때 자식 컬렉션(영업시간·온보딩 단계)을 루트마다 따로 조회한다. 매장 하나를
 * 다룰 때는 상관없지만 목록에서는 매장 수만큼 쿼리가 늘어난다. 실험 기능인 single query loading은 Map 컬렉션을
 * 지원하지 않아 쓸 수 없어서, 목록은 루트 한 번·영업시간 한 번으로 직접 읽는다. 온보딩 단계는 응답에 없으므로 읽지 않는다.
 */
@Component
@RequiredArgsConstructor
class StoreListReader {

    private static final String STORES = StoreSchema.NAME + ".stores";
    private static final String BUSINESS_HOURS = StoreSchema.NAME + ".store_business_hours";

    private final JdbcClient jdbcClient;

    /** storeId → 응답. 삭제된 매장은 빠진다. */
    Map<Long, StoreResponse> responsesOf(Collection<Long> storeIds, Function<String, String> imageUrlOf) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<DayOfWeek, BusinessHourResponse>> hours = new HashMap<>();
        jdbcClient.sql("SELECT store_id, day_of_week, open_time, close_time, closed FROM " + BUSINESS_HOURS
                        + " WHERE store_id IN (:ids)")
                .param("ids", storeIds)
                .query((rs, rowNum) -> hours.computeIfAbsent(rs.getLong("store_id"), id -> new EnumMap<>(DayOfWeek.class))
                        .put(DayOfWeek.valueOf(rs.getString("day_of_week")), new BusinessHourResponse(
                                rs.getObject("open_time", LocalTime.class), rs.getObject("close_time", LocalTime.class),
                                rs.getBoolean("closed"))))
                .list();
        return jdbcClient.sql("SELECT id, name, address, business_registration_number, category, profile_image_key,"
                        + " break_policy, small_business, plan, payday FROM " + STORES
                        + " WHERE id IN (:ids) AND deleted_at IS NULL")
                .param("ids", storeIds)
                .query((rs, rowNum) -> {
                    long id = rs.getLong("id");
                    String category = rs.getString("category");
                    return new StoreResponse(id, rs.getString("name"), rs.getString("address"),
                            rs.getString("business_registration_number"),
                            category == null ? null : StoreCategory.valueOf(category),
                            imageUrlOf.apply(rs.getString("profile_image_key")), hours.getOrDefault(id, Map.of()),
                            BreakPolicy.valueOf(rs.getString("break_policy")), rs.getBoolean("small_business"),
                            StorePlan.valueOf(rs.getString("plan")), rs.getObject("payday", Integer.class));
                })
                .list().stream()
                .collect(Collectors.toMap(StoreResponse::id, Function.identity()));
    }
}
