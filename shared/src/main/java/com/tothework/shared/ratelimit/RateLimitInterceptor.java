package com.tothework.shared.ratelimit;

import com.tothework.shared.error.TooManyRequestsException;
import com.tothework.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RateLimit}이 붙은 엔드포인트의 호출 횟수를 사용자별로 제한한다.
 *
 * <p>카운터는 이 인스턴스의 메모리에만 있다. 서버를 여러 대로 늘리면 대수만큼 한도가 느슨해지므로, 그 시점에는
 * Redis 같은 공유 저장소로 옮겨야 한다. 고정 윈도우 방식이라 경계에서 짧게 한도의 2배까지 몰릴 수 있는데,
 * 목적이 비용 폭주 차단이라 감수한다.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 이 수를 넘으면 만료된 항목을 청소한다 (메모리 무한 증가 방지). */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimitInterceptor(Clock clock) {
        this.clock = clock;
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
        long now = clock.millis();
        long windowMillis = Duration.ofMinutes(rateLimit.windowMinutes()).toMillis();
        sweepIfCrowded(now);

        String key = callerId(request) + "|" + handlerMethod.getMethod().toGenericString();
        Window window = windows.compute(key, (ignored, existing) ->
                existing == null || now >= existing.expiresAt()
                        ? new Window(now + windowMillis, 1)
                        : new Window(existing.expiresAt(), existing.count() + 1));

        if (window.count() > rateLimit.limit()) {
            long retryAfterSeconds = Math.max(1, (window.expiresAt() - now) / 1000);
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

    private void sweepIfCrowded(long now) {
        if (windows.size() < SWEEP_THRESHOLD) {
            return;
        }
        windows.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
    }

    private record Window(long expiresAt, int count) {
    }
}
