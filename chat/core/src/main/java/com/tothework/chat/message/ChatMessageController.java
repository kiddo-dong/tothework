package com.tothework.chat.message;

import com.tothework.shared.security.CurrentUserId;
import com.tothework.shared.web.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이전 메시지 조회. 실시간 통로(STOMP)와 분리해 HTTP로 둔다. 방에 들어오면 클라이언트는 구독을 먼저 걸고 그다음
 * 이것을 부른 뒤 id로 합친다. 순서를 반대로 하면 조회와 구독 사이에 온 메시지를 놓친다.
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/chat/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService service;

    /** 최신부터 id 내림차순. 다음 페이지는 받은 목록의 마지막 id를 {@code before}로 넘긴다. 멤버 누구나. */
    @GetMapping
    public ApiResponse<List<ChatMessageResponse>> getMessages(@PathVariable Long storeId, @CurrentUserId Long userId,
            @RequestParam(required = false) Long before, @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(service.getMessages(storeId, userId, before, size));
    }
}
