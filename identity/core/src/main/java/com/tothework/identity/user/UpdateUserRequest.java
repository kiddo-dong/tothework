package com.tothework.identity.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record UpdateUserRequest(
        @NotBlank String name,
        @NotBlank
        @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다. 예: 010-1234-5678")
        String phone,
        /**
         * 비워 보내면 지금 값을 그대로 둔다. 이름·전화번호와 달리 선택으로 둔 이유는, 이미 이 두
         * 가지만 보내고 있는 화면을 깨지 않기 위해서다.
         */
        @Past(message = "올바른 생년월일을 입력해 주세요.") LocalDate birthDate
) {
}
