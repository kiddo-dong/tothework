package com.tothework.chat.message;

/**
 * 실시간 구독자에게 가는 이벤트. 생성·수정·삭제 세 종류가 같은 구독 주소({@code /topic/stores/{storeId}/chat})로
 * 흐르고, 클라이언트는 {@code message.id}로 화면의 메시지를 추가하거나 바꾼다.
 */
public record ChatEvent(Type type, ChatMessageResponse message) {

    public enum Type {
        CREATED, UPDATED, DELETED
    }
}
