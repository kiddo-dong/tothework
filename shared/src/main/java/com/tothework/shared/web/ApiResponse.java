package com.tothework.shared.web;

/**
 * 모든 응답의 공통 형식. 기존 백엔드와 같은 모양이라 프론트를 고치지 않아도 된다.
 *
 * <p>{@code code}는 실패했을 때만 채워지며, 프론트가 문구가 아니라 값으로 분기할 수 있게 한다.
 */
public record ApiResponse<T>(boolean success, T data, String message, String code) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static ApiResponse<Void> error(String message, String code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
