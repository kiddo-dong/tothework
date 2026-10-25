package com.tothework.laborqa.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question 길이가 곧 임베딩·채팅 비용이고, 세션 질문은 TEXT(64KB) 열에 저장된다. 상한이 없으면 큰 질문 하나가
 *                 LLM 요금을 다 쓴 뒤 저장에서 실패했다.
 */
public record LaborQaRequest(
        @NotBlank
        @Size(max = LaborQaRequest.MAX_QUESTION_LENGTH, message = "질문은 " + LaborQaRequest.MAX_QUESTION_LENGTH
                + "자까지 입력할 수 있어요.")
        String question) {

    public static final int MAX_QUESTION_LENGTH = 2000;
}
