package com.tothework.shared.ratelimit;

import com.tothework.shared.error.TooManyRequestsException;
import com.tothework.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RateLimit}이 붙은 엔드포인트의 호출 횟수를 사용자별로 제한한다.
 *
 * <p>세는 곳은 {@link RateLimitCounter}가 정한다. 운영은 서버가 여러 대라 Redis에서 함께 센다
 * ({@link RedisRateLimitCounter}). 고정 윈도우 방식이라 경계에서 짧게 한도의 2배까지 몰릴 수 있는데, 목적이 비용
 * 폭주 차단이라 감수한다.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitCounter counter;

    public RateLimitInterceptor(RateLimitCounter counter) {
        this.counter = counter;
    }

    /** 이 서버의 메모리에서 센다. 테스트용. */
    public RateLimitInterceptor(Clock clock) {
        this(new InMemoryRateLimitCounter(clock));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }
        String key = callerId(request) + "|" + handlerMethod.getMethod().toGenericString();
        RateLimitCounter.Count count = counter.increment(key, Duration.ofMinutes(rateLimit.windowMinutes()));

        if (count.count() > rateLimit.limit()) {
            long retryAfterSeconds = Math.max(1, count.remaining().toSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            throw new TooManyRequestsException(
                    "요청이 너무 잦습니다. " + retryAfterSeconds + "초 후에 다시 시도해 주세요.");
        }
        return true;
    }

    /** 로그인 사용자는 userId로, 그 외에는 원격 주소로 식별한다. */
    private String callerId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.userId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
