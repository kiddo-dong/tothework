package com.tothework.chat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트가 붙기 전에 채팅을 손으로 확인하는 페이지. {@code static/}에 두면 운영에서도 늘 열리므로, 설정
 * ({@code CHAT_TEST_PAGE_ENABLED=true})을 켰을 때만 이 컨트롤러가 등록된다.
 */
@RestController
@ConditionalOnBooleanProperty("tothework.chat.test-page-enabled")
class ChatTestPageController {

    @GetMapping(value = "/chat-test.html", produces = MediaType.TEXT_HTML_VALUE)
    Resource page() {
        return new ClassPathResource("chat/chat-test.html");
    }
}
