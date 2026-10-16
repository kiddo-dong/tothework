package com.tothework.laborqa.web;

import com.tothework.laborqa.conversation.LaborQaSession;
import java.time.LocalDateTime;

public record LaborQaSessionResponse(Long id, String title, LocalDateTime createdAt) {

    static LaborQaSessionResponse from(LaborQaSession session) {
        return new LaborQaSessionResponse(session.getId(), session.getTitle(), session.getCreatedAt());
    }
}
