package com.tothework.laborqa.answer;

import java.util.List;

/**
 * 상담 답변. JSON 모양은 기존 백엔드의 LaborQaResponse와 같다.
 *
 * @param grounded 우리 자료로 뒷받침된 답인지. 아는 지식만으로 답했으면 false
 */
public record LaborQaAnswer(String answer, List<String> sources, boolean grounded) {
}
