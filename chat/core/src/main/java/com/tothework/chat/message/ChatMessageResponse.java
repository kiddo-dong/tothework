package com.tothework.chat.message;

import java.time.LocalDateTime;

/**
 * 채팅 메시지 응답. REST 조회와 실시간 이벤트가 같은 모양을 쓴다.
 *
 * @param senderName            탈퇴 등으로 사용자를 찾지 못하면 null
 * @param senderProfileImageUrl 사진이 없으면 null
 * @param content               삭제된 메시지는 {@value ChatMessageService#DELETED_CONTENT}
 */
public record ChatMessageResponse(Long id, Long storeId, Long senderId, String senderName,
        String senderProfileImageUrl, String content, boolean deleted, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
