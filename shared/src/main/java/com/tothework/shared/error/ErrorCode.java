package com.tothework.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 실패 응답의 {@code code} 값. 프론트가 이 값으로 분기하므로 기존 백엔드와 이름을 똑같이 유지한다.
 *
 * <p>가입·인증에 관한 값(EMAIL_NOT_VERIFIED 등)도 여기 둔다. 서비스별로 따로 정의하면 같은 화면이 서비스마다
 * 다른 값을 받게 되는데, 이 값들은 API 계약의 일부라 한곳에서 관리하는 편이 안전하다.
 */
public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "해당 작업에 대한 권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 완료되지 않았습니다."),
    PROFILE_INCOMPLETE(HttpStatus.FORBIDDEN, "추가 정보 입력이 완료되지 않았습니다."),
    TOKEN_ALREADY_ROTATED(HttpStatus.CONFLICT, "다른 창에서 로그인이 먼저 갱신됐습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
