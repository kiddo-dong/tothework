package com.tothework.chat.message;

/** 메시지 삭제 ({@code /app/stores/{storeId}/chat.delete}). */
public record DeleteChatMessageRequest(Long messageId) {
}
