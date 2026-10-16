package com.tothework.workforce.employment;

import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.config.WorkforceSchema;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 매장 전체의 고용 조건을 한 번에 읽는다.
 *
 * <p>{@link MemberEmployment}를 저장소로 여러 개 읽으면 Spring Data JDBC가 근무 가능 요일을 멤버마다 따로 조회해
 * 직원 수만큼 쿼리가 늘어난다. 목록은 조건 한 번·요일 한 번으로 직접 읽는다. 쓰기는 계속 애그리거트로 한다.
 */
@Component
@RequiredArgsConstructor
class EmploymentTermsReader {

    private static final String TERMS = WorkforceSchema.NAME + ".employment_terms";
    private static final String AVAILABLE_DAYS = WorkforceSchema.NAME + ".employment_available_days";

    private final JdbcClient jdbcClient;

    /** memberId → 고용 조건. 행이 아직 없는 멤버는 빠진다. */
    Map<Long, EmploymentTerms> ofStore(Long storeId) {
        Map<Long, Set<DayOfWeek>> days = new HashMap<>();
        jdbcClient.sql("SELECT d.member_id, d.day_of_week FROM " + AVAILABLE_DAYS + " d JOIN " + TERMS
                        + " t ON t.member_id = d.member_id WHERE t.store_id = :storeId")
                .param("storeId", storeId)
                .query((rs, rowNum) -> days.computeIfAbsent(rs.getLong("member_id"), id -> EnumSet.noneOf(DayOfWeek.class))
                        .add(DayOfWeek.valueOf(rs.getString("day_of_week"))))
                .list();
        return jdbcClient.sql("SELECT member_id, store_id, hourly_wage, tax_mode, weekly_holiday_day FROM " + TERMS
                        + " WHERE store_id = :storeId")
                .param("storeId", storeId)
                .query((rs, rowNum) -> {
                    long memberId = rs.getLong("member_id");
                    String holiday = rs.getString("weekly_holiday_day");
                    return new EmploymentTerms(memberId, rs.getLong("store_id"), rs.getInt("hourly_wage"),
                            TaxMode.valueOf(rs.getString("tax_mode")),
                            Set.copyOf(days.getOrDefault(memberId, Set.of())),
                            holiday == null ? null : DayOfWeek.valueOf(holiday));
                })
                .list().stream()
                .collect(Collectors.toMap(EmploymentTerms::memberId, Function.identity()));
    }
}
