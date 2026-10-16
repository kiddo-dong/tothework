package com.tothework.shared.web;

import com.tothework.shared.ratelimit.RateLimitInterceptor;
import com.tothework.shared.security.ProfileCompletionInterceptor;
import java.time.Clock;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 인터셉터가 @RateLimit이 붙은 핸들러만 골라내므로 경로는 전체로 둔다.
        registry.addInterceptor(new RateLimitInterceptor(Clock.systemUTC())).addPathPatterns("/api/**");
        // 가입이 끝나지 않은 계정은 비용이 드는 처리에 들어가기 전에 막도록 rate limit 다음에 둔다.
        registry.addInterceptor(new ProfileCompletionInterceptor()).addPathPatterns("/api/**");
    }
}
