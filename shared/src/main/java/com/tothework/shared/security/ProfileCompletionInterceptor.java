package com.tothework.shared.security;

import com.tothework.shared.error.ProfileIncompleteException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 추가 정보 입력을 끝내지 않은 계정의 서비스 API 호출을 막는다.
 *
 * <p>소셜 로그인은 이름과 이메일만 받아오므로 가입 직후 전화번호·생년월일·약관 동의가 비어 있다. 프론트가 입력
 * 화면으로 보내 주긴 하지만, API를 직접 부르면 그대로 통과해 <b>약관에 동의하지 않은 사용자가 서비스를 쓰게
 * 되므로</b> 서버에서도 막는다. 모든 서비스에 같은 규칙이라 공통으로 둔다.
 *
 * <p>비인증 요청은 그대로 통과시켜 원래대로 401이 나가게 둔다.
 */
public class ProfileCompletionInterceptor implements HandlerInterceptor {

    /**
     * 미완성 상태에서도 허용하는 경로. 추가 정보를 입력하려면 자기 정보를 읽고(GET) 제출할 수 있어야 하고,
     * 그만두려면 탈퇴(DELETE)할 수 있어야 한다. 같은 경로의 PATCH(프로필 수정)는 막아 우회를 막는다.
     */
    private static final String ME = "/api/v1/users/me";
    private static final String COMPLETE_PROFILE = ME + "/complete-profile";
    private static final String AUTH_PREFIX = "/api/v1/auth/";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isAllowedWhileIncomplete(request)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                && !user.profileCompleted()) {
            throw new ProfileIncompleteException("추가 정보를 입력해야 서비스를 이용할 수 있습니다.");
        }
        return true;
    }

    private boolean isAllowedWhileIncomplete(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith(AUTH_PREFIX) || COMPLETE_PROFILE.equals(path)) {
            return true;
        }
        return ME.equals(path)
                && (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.DELETE.matches(request.getMethod()));
    }
}
