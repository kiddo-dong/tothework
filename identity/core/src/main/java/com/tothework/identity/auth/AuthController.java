package com.tothework.identity.auth;

import com.tothework.identity.config.IdentityProperties;
import com.tothework.identity.user.AuthProvider;
import com.tothework.shared.security.JwtProperties;
import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.error.InvalidRequestException;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 리프레시 토큰 쿠키 이름. HttpOnly라 JS에서 읽을 수 없고, 브라우저가 /api/v1/auth 요청에만 자동 첨부한다. */
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String AUTH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;

    private final JwtProperties jwtProperties;
    private final IdentityProperties properties;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Long>> signup(@Valid @RequestBody SignupRequest request) {
        Long userId = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(userId));
    }

    /** 액세스 토큰은 본문으로, 리프레시 토큰은 HttpOnly 쿠키로 내려간다 (XSS 탈취 방지). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponseWithCookie(authService.login(request));
    }

    /** 본문 없이 호출한다. 리프레시 토큰은 브라우저가 쿠키로 자동 첨부하며, 재발급 시 쿠키도 새 토큰으로 교체된다. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRequestException("리프레시 토큰 쿠키가 없습니다. 다시 로그인해 주세요.");
        }
        return tokenResponseWithCookie(authService.refresh(refreshToken));
    }

    /**
     * 서버에 저장된 리프레시 토큰을 폐기하고 쿠키도 만료시킨다. 쿠키만 지우면 토큰 자체는 살아 있어
     * 값을 미리 복사해 둔 쪽이 계속 재발급을 받을 수 있으므로, 서버 기록을 지우는 쪽이 실제 로그아웃이다.
     * 액세스 토큰은 별도 폐기 수단이 없고 15분이면 만료되므로 클라이언트가 버리면 된다.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        ResponseCookie expiredCookie = refreshCookieBuilder("").maxAge(Duration.ZERO).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(ApiResponse.ok());
    }

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<ApiResponse<TokenResponse>> oauthLogin(@PathVariable String provider,
            @Valid @RequestBody OAuthLoginRequest request) {
        AuthProvider authProvider = parseProvider(provider);
        return tokenResponseWithCookie(authService.oauthLogin(authProvider, request.accessToken()));
    }

    /**
     * 메일의 인증 링크가 향하는 곳. 사용자가 브라우저로 직접 여는 유일한 API라서, 응답 JSON을
     * 그대로 보여주는 대신 결과만 붙여 프론트 로그인 화면으로 넘긴다.
     *
     * <p>만료되거나 이미 쓴 링크도 에러 응답으로 끊지 않는다. 사용자는 안내 화면을 봐야 하는데
     * 그건 프론트가 할 일이기 때문이다.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        String result;
        try {
            authService.verifyEmail(token);
            result = "success";
        } catch (InvalidRequestException e) {
            result = "invalid";
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(properties.frontendUrl() + "/login?verified=" + result))
                .build();
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ApiResponse.ok();
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponseWithCookie(IssuedTokens tokens) {
        ResponseCookie refreshCookie = refreshCookieBuilder(tokens.refreshToken())
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenExpirationMs()))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(new TokenResponse(tokens.accessToken(), tokens.profileCompleted())));
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookieBuilder(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .path(AUTH_COOKIE_PATH)
                .sameSite("Lax");
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("지원하지 않는 로그인 방식입니다.");
        }
    }
}
