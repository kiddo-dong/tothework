package com.tothework.operations.checklist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 체크리스트 항목 일괄 등록 요청.
 *
 * <p>배열 순서가 곧 화면에 뜨는 순서다. 항목별 {@code displayOrder}는 여기서 무시하고 순서로 매긴다
 * — 프리셋을 보내는 쪽이 번호를 따로 계산하게 만들 이유가 없다.
 */
public record ChecklistItemBulkRequest(
        @NotEmpty(message = "등록할 항목이 없습니다.")
        @Size(max = MAX_ITEMS, message = "한 번에 " + MAX_ITEMS + "개까지 등록할 수 있습니다.")
        @Valid List<ChecklistItemRequest> items
) {
    /** 업종 프리셋이 10개 안팎이라 넉넉하게 잡은 상한. 실수나 악용으로 큰 배열이 오는 것만 막는다. */
    public static final int MAX_ITEMS = 100;
}
