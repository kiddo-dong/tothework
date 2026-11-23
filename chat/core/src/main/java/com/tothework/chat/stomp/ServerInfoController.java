package com.tothework.chat.stomp;

import com.tothework.chat.config.ChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * 클라이언트가 붙은 서버의 이름. {@code /app/server-info}를 구독하면 브로커를 거치지 않고 그 구독에 한 번 답한다.
 *
 * <p>서버가 여러 대일 때 두 탭이 서로 다른 서버에 붙었는지 확인하는 용도다. 서버 이름 말고는 아무것도 내보내지 않는다.
 */
@Controller
@RequiredArgsConstructor
class ServerInfoController {

    static final String DESTINATION = "/server-info";

    private final ChatProperties properties;

    @SubscribeMapping(DESTINATION)
    ServerInfo serverInfo() {
        return new ServerInfo(properties.serverId());
    }

    record ServerInfo(String serverId) {
    }
}
