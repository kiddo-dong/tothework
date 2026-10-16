package com.tothework.workforce.shift;

import java.util.List;

/**
 * AI가 제안한 스케줄 초안. accepted는 기존 ShiftService의 법정 검증(근무가능요일·영업시간·연소자보호·중복·주간상한)을
 * 통과한 항목만, rejected는 검증에 실패한 항목과 그 사유다. 이 응답은 저장되지 않으며, 사용자가 확인 후
 * /shifts/ai-draft/confirm 으로 다시 보내야 실제 스케줄이 생성된다.
 */
public record ScheduleDraftResponse(
        List<ScheduleDraftItem> accepted,
        List<RejectedScheduleDraftItem> rejected,
        String aiNote
) {
}
