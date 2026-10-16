package com.tothework.identity.user;

import java.time.LocalDate;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        LocalDate birthDate,
        String profileImageUrl,
        boolean profileCompleted,
        boolean emailVerified,
        /** 가입 방식. LOCAL만 비밀번호가 있어, 비밀번호 변경 메뉴를 보여 줄지 판단하는 데 쓴다. */
        AuthProvider provider
) {

    static UserResponse from(User user, String profileImageUrl) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.getBirthDate(), profileImageUrl, user.isProfileCompleted(), user.isEmailVerified(),
                user.getProvider());
    }
}
