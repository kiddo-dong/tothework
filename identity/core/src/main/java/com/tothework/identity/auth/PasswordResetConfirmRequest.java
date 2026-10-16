package com.tothework.identity.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String newPassword,
        @NotBlank String newPasswordConfirm
) {
}
