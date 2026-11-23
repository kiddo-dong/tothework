package com.tothework.chat.message;

import com.tothework.chat.message.ChatEvent.Type;
import com.tothework.chat.stomp.StompPrincipal;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * 채팅 전송·수정·삭제 (STOMP SEND {@code /app/stores/{storeId}/chat.*}).
 *
 * <p>순서: 서비스가 저장하고 트랜잭션이 커밋된 뒤에 방송한다. 저장이 실패하면 예외가 {@link ChatMessageExceptionAdvice}로
 * 가서 보낸 사람에게만 오류가 가고, 방송은 일어나지 않는다. 저장 뒤 방송(Redis 발행)만 실패하면 메시지는 남아 있고
 * 보낸 사람에게 그 사실을 알린다.
 */
@Controller
@RequiredArgsConstructor
class ChatStompController {

    private final ChatMessageService service;
    private final ChatEventPublisher publisher;

    @MessageMapping("/stores/{storeId}/chat.send")
    void send(@DestinationVariable Long storeId, @Payload SendChatMessageRequest request, Principal principal) {
        ChatMessageResponse message = service.send(storeId, userIdOf(principal), request.content());
        publisher.publish(storeId, new ChatEvent(Type.CREATED, message));
    }

    @MessageMapping("/stores/{storeId}/chat.edit")
    void edit(@DestinationVariable Long storeId, @Payload EditChatMessageRequest request, Principal principal) {
        ChatMessageResponse message = service.edit(storeId, userIdOf(principal), request.messageId(),
                request.content());
        publisher.publish(storeId, new ChatEvent(Type.UPDATED, message));
    }

    @MessageMapping("/stores/{storeId}/chat.delete")
    void delete(@DestinationVariable Long storeId, @Payload DeleteChatMessageRequest request, Principal principal) {
        ChatMessageResponse message = service.delete(storeId, userIdOf(principal), request.messageId());
        publisher.publish(storeId, new ChatEvent(Type.DELETED, message));
    }

    /** 인터셉터가 CONNECT에서 정한 주체. 그 단계를 거치지 않은 프레임은 여기까지 오지 못한다. */
    private static Long userIdOf(Principal principal) {
        return ((StompPrincipal) principal).userId();
    }
}
