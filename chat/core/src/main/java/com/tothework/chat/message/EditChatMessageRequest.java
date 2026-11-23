package com.tothework.chat.message;

/** 메시지 수정 ({@code /app/stores/{storeId}/chat.edit}). */
public record EditChatMessageRequest(Long messageId, String content) {
}
