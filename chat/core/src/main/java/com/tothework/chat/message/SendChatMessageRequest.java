package com.tothework.chat.message;

/** 메시지 전송 ({@code /app/stores/{storeId}/chat.send}). 내용 검증은 {@link ChatMessage}가 한다. */
public record SendChatMessageRequest(String content) {
}
