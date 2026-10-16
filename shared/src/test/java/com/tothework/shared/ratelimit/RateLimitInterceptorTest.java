package com.tothework.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tothework.shared.error.TooManyRequestsException;
import com.tothework.shared.security.AuthenticatedUser;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

class RateLimitInterceptorTest {

    private MutableClock clock;
    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-05T09:00:00Z"));
        interceptor = new RateLimitInterceptor(clock);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        authenticateAs(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsCallsUpToTheLimit() throws Exception {
        HandlerMethod handler = handlerFor("limitedThreePerMinute");

        for (int i = 0; i < 3; i++) {
            assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        }
    }

    @Test
    void rejectsTheCallThatExceedsTheLimit() throws Exception {
        HandlerMethod handler = handlerFor("limitedThreePerMinute");
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, handler);
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("잦습니다");
    }

    @Test
    void setsRetryAfterHeaderWhenRejecting() throws Exception {
        HandlerMethod handler = handlerFor("limitedThreePerMinute");
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, handler);
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(TooManyRequestsException.class);

        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void allowsAgainAfterTheWindowExpires() throws Exception {
        HandlerMethod handler = handlerFor("limitedThreePerMinute");
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, handler);
        }
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(TooManyRequestsException.class);

        clock.advance(Duration.ofMinutes(1));

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }

    @Test
    void countsSeparatelyPerUser() throws Exception {
        HandlerMethod handler = handlerFor("limitedThreePerMinute");
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, handler);
        }

        authenticateAs(2L); // 다른 사용자는 앞 사용자의 소진과 무관해야 한다

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }

    @Test
    void countsSeparatelyPerEndpoint() throws Exception {
        HandlerMethod limited = handlerFor("limitedThreePerMinute");
        HandlerMethod other = handlerFor("limitedOncePerMinute");
        for (int i = 0; i < 3; i++) {
            interceptor.preHandle(request, response, limited);
        }

        assertThat(interceptor.preHandle(request, response, other)).isTrue();
    }

    @Test
    void doesNotLimitEndpointsWithoutTheAnnotation() throws Exception {
        HandlerMethod handler = handlerFor("notLimited");

        for (int i = 0; i < 50; i++) {
            assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        }
    }

    @Test
    void ignoresNonControllerHandlers() throws Exception {
        // 정적 리소스 핸들러 등 HandlerMethod가 아닌 요청은 그냥 통과시킨다.
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void fallsBackToRemoteAddressWhenUnauthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        HandlerMethod handler = handlerFor("limitedOncePerMinute");
        request.setRemoteAddr("10.0.0.1");

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(TooManyRequestsException.class);

        MockHttpServletRequest otherIp = new MockHttpServletRequest();
        otherIp.setRemoteAddr("10.0.0.2");
        assertThatCode(() -> interceptor.preHandle(otherIp, response, handler)).doesNotThrowAnyException();
    }

    private void authenticateAs(long userId) {
        AuthenticatedUser principal = new TestUser(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    private HandlerMethod handlerFor(String methodName) throws NoSuchMethodException {
        Method method = DummyController.class.getMethod(methodName);
        return new HandlerMethod(new DummyController(), method);
    }

    @SuppressWarnings("unused")
    static class DummyController {

        @RateLimit(limit = 3, windowMinutes = 1)
        public void limitedThreePerMinute() {
        }

        @RateLimit(limit = 1, windowMinutes = 1)
        public void limitedOncePerMinute() {
        }

        public void notLimited() {
        }
    }

    /** 윈도우 만료를 테스트에서 직접 앞당기기 위한 조작 가능한 시계. */
    static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private record TestUser(Long userId) implements AuthenticatedUser {

        @Override
        public boolean profileCompleted() {
            return true;
        }
    }
}
