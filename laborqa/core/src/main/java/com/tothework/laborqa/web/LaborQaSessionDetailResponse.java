package com.tothework.laborqa.web;

import com.tothework.laborqa.conversation.LaborQaMessage;
import com.tothework.laborqa.conversation.LaborQaSession;
import java.util.List;

public record LaborQaSessionDetailResponse(Long id, String title, List<LaborQaMessageResponse> messages) {

    static LaborQaSessionDetailResponse from(LaborQaSession session, List<LaborQaMessage> messages) {
        return new LaborQaSessionDetailResponse(session.getId(), session.getTitle(),
                messages.stream().map(LaborQaMessageResponse::from).toList());
    }
}
