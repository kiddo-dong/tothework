package com.tothework.chat.message;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 이벤트 방송의 유일한 출구. 방송은 반드시 이 클래스를 거친다.
 *
 * <p>지금은 이 서버에 연결된 구독자에게만 보낸다. 서버가 여러 대가 되면 이 클래스만 Redis 발행으로 바뀐다.
 */
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(Long storeId, ChatEvent event) {
        messagingTemplate.convertAndSend(topicOf(storeId), event);
    }

    public static String topicOf(Long storeId) {
        return "/topic/stores/" + storeId + "/chat";
    }
}
