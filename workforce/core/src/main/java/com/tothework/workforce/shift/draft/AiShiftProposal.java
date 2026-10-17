package com.tothework.workforce.shift.draft;

import java.time.LocalDate;
import java.time.LocalTime;

/** LLM 응답 JSON을 역직렬화하기 위한 내부 전용 DTO. 신뢰하지 않는 입력이므로 이 값 그대로 저장하지 않고 반드시
 * ShiftService의 기존 검증을 통과시킨 뒤에만 사용한다. */
public record AiShiftProposal(
        Long storeMemberId,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime
) {
}
