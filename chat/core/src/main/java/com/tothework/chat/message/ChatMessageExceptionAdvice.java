package com.tothework.chat.message;

import com.tothework.shared.error.BusinessException;
import com.tothework.shared.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * 애플리케이션 수준 오류(남의 메시지 수정, 빈 메시지, 없는 메시지 등)를 보낸 사람의 {@code /user/queue/errors}로만
 * 보낸다. 연결은 유지된다. 이런 오류를 ERROR 프레임으로 보내면 연결이 끊기므로 섞지 않는다.
 *
 * <p>{@code broadcast = false}: 같은 사용자가 여러 탭으로 붙어 있어도 요청을 보낸 세션에만 간다. 본문은 HTTP와 같은
 * {@link ApiResponse} 형식이다. 오류 응답은 Redis를 거치지 않는다. 요청이 들어온 서버에 그 세션이 있기 때문이다.
 */
@Slf4j
@ControllerAdvice(assignableTypes = ChatStompController.class)
class ChatMessageExceptionAdvice {

    static final String ERROR_QUEUE = "/queue/errors";

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.error(e.getMessage(), e.getErrorCode().name());
    }

    /** 저장은 됐지만 서버 간 전달(Redis)이 실패했다. 이 오류도 Redis를 거치지 않고 이 서버의 세션으로 바로 간다. */
    @MessageExceptionHandler(ChatBroadcastException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    ApiResponse<Void> handleBroadcastFailure(ChatBroadcastException e) {
        return ApiResponse.error(e.getMessage());
    }

    /** 깨진 JSON 등 본문 변환 실패. */
    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    ApiResponse<Void> handleConversion(MessageConversionException e) {
        return ApiResponse.error("요청 본문을 해석할 수 없습니다. 필드 형식과 값을 확인해 주세요.");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("채팅 메시지 처리 실패", e);
        return ApiResponse.error("서버 오류가 발생했습니다.");
    }
}
