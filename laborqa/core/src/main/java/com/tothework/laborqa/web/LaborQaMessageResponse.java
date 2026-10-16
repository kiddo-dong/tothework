package com.tothework.laborqa.web;

import com.tothework.laborqa.conversation.LaborQaMessage;
import com.tothework.laborqa.conversation.LaborQaRole;
import java.time.LocalDateTime;
import java.util.List;

public record LaborQaMessageResponse(Long id, LaborQaRole role, String content, List<String> sources,
        LocalDateTime createdAt) {

    static LaborQaMessageResponse from(LaborQaMessage message) {
        return new LaborQaMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getSourceList(), message.getCreatedAt());
    }
}
