package com.tothework.chat.message;

/**
 * 저장은 됐지만 실시간 전달(Redis 발행)에 실패했다. 보낸 사람의 개인 오류 큐로만 알린다. 메시지는 DB에 있으므로
 * 새로 고치거나 재접속하면 보인다.
 */
public class ChatBroadcastException extends RuntimeException {

    public ChatBroadcastException() {
        super("메시지는 저장됐지만 실시간 전달에 실패했습니다. 다른 사람에게는 새로고침 후 보입니다.");
    }
}
