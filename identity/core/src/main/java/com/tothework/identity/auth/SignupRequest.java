package com.tothework.identity.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotBlank String passwordConfirm,
        @NotBlank String name,
        @NotBlank
        @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다. 예: 010-1234-5678")
        String phone,
        @NotNull @Past(message = "올바른 생년월일을 입력해 주세요.") LocalDate birthDate,
        @AssertTrue(message = "약관에 동의해야 합니다.") boolean agreedToTerms
) {
}
