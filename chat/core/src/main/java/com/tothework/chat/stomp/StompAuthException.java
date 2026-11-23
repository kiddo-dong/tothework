package com.tothework.chat.stomp;

/** STOMP 프레임 단계의 인증 실패. ERROR 프레임으로 나가고 연결이 끊긴다. */
public class StompAuthException extends RuntimeException {

    public StompAuthException(String message) {
        super(message);
    }
}
