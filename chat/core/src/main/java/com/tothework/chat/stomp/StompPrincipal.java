package com.tothework.chat.stomp;

import java.security.Principal;

/**
 * STOMP 세션의 주체. CONNECT에서 정해져 세션이 끝날 때까지 유지된다.
 *
 * <p>{@link #getName()}이 사용자 id다. {@code /user/queue/errors} 같은 개인 주소는 이 이름으로 세션을 찾는다.
 */
public record StompPrincipal(Long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
