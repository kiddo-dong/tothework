package com.tothework.chat.stomp;

import com.tothework.shared.error.BusinessException;
import com.tothework.shared.error.ErrorCode;
import com.tothework.shared.web.ApiResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프로토콜 수준 오류(토큰 없음·만료, 소속 아닌 매장 구독 등)를 ERROR 프레임으로 보낸다. ERROR 프레임을 받으면 연결이
 * 끊긴다.
 *
 * <p>본문은 HTTP와 같은 {@link ApiResponse} 형식이고 {@code message} 헤더에도 같은 문구를 싣는다. 원인을 알 수 없는
 * 예외의 내부 문구는 내보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private final JsonMapper jsonMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        ApiResponse<Void> body = toResponse(ex);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(body.message());
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);
        byte[] payload = jsonMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    private static ApiResponse<Void> toResponse(Throwable ex) {
        Throwable cause = rootCause(ex);
        if (cause instanceof StompAuthException) {
            return ApiResponse.error(cause.getMessage(), ErrorCode.UNAUTHORIZED.name());
        }
        if (cause instanceof BusinessException business) {
            return ApiResponse.error(business.getMessage(), business.getErrorCode().name());
        }
        log.warn("STOMP 프레임 처리 실패", ex);
        return ApiResponse.error("요청을 처리할 수 없습니다.", ErrorCode.INVALID_REQUEST.name());
    }

    /** 인터셉터의 예외는 MessageDeliveryException 등에 감싸여 온다. */
    private static Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current
                && !(current instanceof StompAuthException) && !(current instanceof BusinessException)) {
            current = current.getCause();
        }
        return current;
    }
}
