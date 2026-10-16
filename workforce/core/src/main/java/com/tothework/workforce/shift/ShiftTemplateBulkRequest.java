package com.tothework.workforce.shift;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 근무 유형 일괄 등록 요청.
 *
 * <p>배열 순서가 곧 화면에 뜨는 순서다. 항목별 {@code displayOrder}는 여기서 무시하고 순서로 매긴다.
 */
public record ShiftTemplateBulkRequest(
        @NotEmpty(message = "등록할 근무 유형이 없습니다.")
        @Size(max = MAX_ITEMS, message = "한 번에 " + MAX_ITEMS + "개까지 등록할 수 있습니다.")
        @Valid List<ShiftTemplateRequest> items
) {
    public static final int MAX_ITEMS = 50;
}
