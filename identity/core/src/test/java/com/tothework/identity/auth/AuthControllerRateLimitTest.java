package com.tothework.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.tothework.shared.ratelimit.RateLimit;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 로그인 없이 열린 인증 API 중 비밀번호를 대입할 수 있거나 메일을 보내는 곳에는 호출 제한이 있어야 한다. 제한 자체의
 * 동작은 RateLimitInterceptorTest가 보고, 여기서는 붙어 있는지만 지킨다.
 */
class AuthControllerRateLimitTest {

    @Test
    void publicEndpointsThatCanBeAbusedAreRateLimited() {
        for (String name : new String[] {"signup", "login", "oauthLogin", "resendVerification",
                "requestPasswordReset", "confirmPasswordReset"}) {
            assertThat(method(name).getAnnotation(RateLimit.class)).as(name).isNotNull();
        }
    }

    /** 메일을 보내는 곳은 10분에 몇 통으로 빡빡하게 둔다. */
    @Test
    void mailSendingEndpointsAreTight() {
        for (String name : new String[] {"resendVerification", "requestPasswordReset"}) {
            RateLimit limit = method(name).getAnnotation(RateLimit.class);
            assertThat(limit.limit()).as(name).isLessThanOrEqualTo(5);
            assertThat(limit.windowMinutes()).as(name).isGreaterThanOrEqualTo(10);
        }
    }

    private static Method method(String name) {
        return Arrays.stream(AuthController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
