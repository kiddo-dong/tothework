package com.tothework.identity.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인한 상태에서 비밀번호를 바꿀 때. 지금 비밀번호를 함께 받는 이유는, 자리를 비운 사이 남이
 * 열린 화면으로 비밀번호를 바꿔버리는 것을 막기 위해서다.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String newPassword,
        @NotBlank String newPasswordConfirm
) {
}
