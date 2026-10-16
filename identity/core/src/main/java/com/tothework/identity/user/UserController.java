package com.tothework.identity.user;

import com.tothework.identity.auth.AuthService;
import com.tothework.identity.auth.ChangePasswordRequest;
import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    /** 비밀번호 규칙과 세션 폐기가 모두 여기 있어, 경로만 /users/me 아래에 두고 처리는 위임한다. */
    private final AuthService authService;

    @GetMapping
    public ApiResponse<UserResponse> getMe(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(@CurrentUserId Long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateMe(userId, request));
    }

    @PostMapping("/complete-profile")
    public ApiResponse<UserResponse> completeProfile(@CurrentUserId Long userId,
            @Valid @RequestBody CompleteProfileRequest request) {
        return ApiResponse.success(userService.completeProfile(userId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(@CurrentUserId Long userId) {
        userService.withdraw(userId);
        return ApiResponse.ok();
    }

    /** 로그인 상태에서 비밀번호 변경. 성공하면 모든 기기에서 로그아웃되므로 다시 로그인해야 한다. */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@CurrentUserId Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/profile-image")
    public ApiResponse<UserResponse> updateProfileImage(@CurrentUserId Long userId,
            @RequestParam("image") MultipartFile image) {
        return ApiResponse.success(userService.updateProfileImage(userId, image));
    }

    @DeleteMapping("/profile-image")
    public ApiResponse<UserResponse> deleteProfileImage(@CurrentUserId Long userId) {
        return ApiResponse.success(userService.deleteProfileImage(userId));
    }
}
