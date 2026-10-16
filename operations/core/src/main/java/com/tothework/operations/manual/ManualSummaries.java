package com.tothework.operations.manual;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 매뉴얼 목록. 요약에는 이미지가 없으므로 루트 행만 한 번에 읽는다. */
@Component
@RequiredArgsConstructor
class ManualSummaries {

    private final JdbcClient jdbcClient;

    List<ManualSummaryResponse> ofStore(Long storeId) {
        return jdbcClient.sql("SELECT id, category, title, display_order, updated_at FROM " + OperationsSchema.NAME
                        + ".manuals WHERE store_id = :storeId ORDER BY category, display_order, id")
                .param("storeId", storeId)
                .query((rs, rowNum) -> new ManualSummaryResponse(rs.getLong("id"), rs.getString("category"),
                        rs.getString("title"), rs.getInt("display_order"),
                        rs.getObject("updated_at", LocalDateTime.class)))
                .list();
    }
}
