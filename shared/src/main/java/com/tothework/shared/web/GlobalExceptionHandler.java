package com.tothework.shared.web;

import com.tothework.shared.error.BusinessException;
import com.tothework.shared.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 모든 서비스에 공통인 예외 → 응답 변환. 메시지와 code는 기존 백엔드와 같다.
 *
 * <p>로그인 실패와 업로드 오류도 여기서 처리한다. 여러 서비스가 같은 메시지를 내야 하는 공통 규칙이라서다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getMessage(), e.getErrorCode().name()));
    }

    /** 로그인 실패. 계정이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않아 계정 존재 여부를 드러내지 않는다. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    /** 업로드 파일 크기 초과. 한도는 spring.servlet.multipart.max-file-size(10MB). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("업로드 파일이 최대 허용 크기(10MB)를 초과했습니다."));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("필수 업로드 파일이 누락되었습니다: " + e.getRequestPartName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    /** 깨진 JSON, 잘못된 enum 값 등 요청 본문 파싱 실패. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("요청 본문을 해석할 수 없습니다. 필드 형식과 값을 확인해 주세요."));
    }

    /** 경로 변수·쿼리 파라미터 타입 불일치 (예: storeId에 문자열). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("파라미터 형식이 올바르지 않습니다: " + e.getName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("필수 파라미터가 누락되었습니다: " + e.getParameterName()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("필수 헤더가 누락되었습니다: " + e.getHeaderName()));
    }

    /** 같은 데이터를 다른 사람이 먼저 바꿨다. 늦은 쪽의 변경은 이미 되돌아갔으므로 화면을 새로 받게 한다. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("다른 사람이 먼저 처리했어요. 새로고침한 뒤 다시 확인해 주세요.",
                        ErrorCode.CONFLICT.name()));
    }

    /** DB 무결성 위반 (동시 가입 등 unique 충돌, 참조 중인 데이터 삭제 시도). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("이미 존재하거나 다른 데이터가 참조 중이어서 처리할 수 없습니다.",
                        ErrorCode.CONFLICT.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 오류가 발생했습니다."));
    }
}
