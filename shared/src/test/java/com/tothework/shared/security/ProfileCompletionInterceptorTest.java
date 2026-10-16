package com.tothework.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tothework.shared.error.ProfileIncompleteException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 추가 정보를 입력하지 않은 계정을 서버가 직접 막는지 확인한다. 프론트의 화면 이동만으로는
 * 주소창에 URL을 치거나 API를 직접 부르는 경우를 막지 못한다.
 */
class ProfileCompletionInterceptorTest {

    private final ProfileCompletionInterceptor interceptor = new ProfileCompletionInterceptor();
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void givenLoggedInUser(boolean profileCompleted) {
        AuthenticatedUser userDetails = mock(AuthenticatedUser.class);
        when(userDetails.profileCompleted()).thenReturn(profileCompleted);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, java.util.List.of()));
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    @Test
    void blocksServiceCallsWhileTheProfileIsIncomplete() {
        givenLoggedInUser(false);

        assertThatThrownBy(() -> interceptor.preHandle(request("GET", "/api/v1/stores"), response, null))
                .isInstanceOf(ProfileIncompleteException.class);
    }

    @Test
    void allowsServiceCallsOnceTheProfileIsComplete() {
        givenLoggedInUser(true);

        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores"), response, null)).isTrue();
    }

    /** 추가 정보를 제출할 길까지 막으면 이 상태에서 빠져나갈 수 없다. */
    @Test
    void allowsSubmittingTheMissingProfile() {
        givenLoggedInUser(false);

        assertThat(interceptor.preHandle(
                request("POST", "/api/v1/users/me/complete-profile"), response, null)).isTrue();
    }

    /** 무엇이 비었는지 읽어야 입력 화면을 그릴 수 있다. */
    @Test
    void allowsReadingOwnAccount() {
        givenLoggedInUser(false);

        assertThat(interceptor.preHandle(request("GET", "/api/v1/users/me"), response, null)).isTrue();
    }

    /** 입력하지 않고 그만두는 선택도 남겨둔다. */
    @Test
    void allowsWithdrawing() {
        givenLoggedInUser(false);

        assertThat(interceptor.preHandle(request("DELETE", "/api/v1/users/me"), response, null)).isTrue();
    }

    /** 같은 경로의 PATCH로 이름만 바꾸며 추가 정보 입력을 건너뛰지 못하게 한다. */
    @Test
    void blocksEditingTheProfileInsteadOfCompletingIt() {
        givenLoggedInUser(false);

        assertThatThrownBy(() -> interceptor.preHandle(request("PATCH", "/api/v1/users/me"), response, null))
                .isInstanceOf(ProfileIncompleteException.class);
    }

    /** 로그아웃·재발급은 미완성 상태에서도 되어야 한다. */
    @Test
    void allowsAuthEndpoints() {
        givenLoggedInUser(false);

        assertThat(interceptor.preHandle(request("POST", "/api/v1/auth/logout"), response, null)).isTrue();
    }

    /** 인증 여부 판단은 Spring Security의 몫이라, 비로그인 요청은 그대로 흘려보낸다. */
    @Test
    void leavesUnauthenticatedRequestsToSpringSecurity() {
        assertThat(interceptor.preHandle(request("GET", "/api/v1/stores"), response, null)).isTrue();
    }
}
