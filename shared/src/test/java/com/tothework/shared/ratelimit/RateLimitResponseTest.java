package com.tothework.shared.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tothework.shared.web.GlobalExceptionHandler;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인터셉터가 던진 예외가 GlobalExceptionHandler를 타고 429 응답으로 나가는지 확인한다.
 * (preHandle에서 던진 예외는 컨트롤러 밖에서 발생하므로 @RestControllerAdvice가 잡는지 별도 검증이 필요하다)
 */
class RateLimitResponseTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LimitedController())
                .addInterceptors(new RateLimitInterceptor(Clock.systemUTC()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exceedingTheLimitReturns429WithErrorBody() throws Exception {
        mockMvc.perform(post("/test/limited")).andExpect(status().isOk());

        mockMvc.perform(post("/test/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("잦습니다")));
    }

    @RestController
    static class LimitedController {

        @RateLimit(limit = 1, windowMinutes = 1)
        @PostMapping("/test/limited")
        public String limited() {
            return "ok";
        }
    }
}
