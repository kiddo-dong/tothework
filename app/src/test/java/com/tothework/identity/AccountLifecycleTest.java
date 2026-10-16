package com.tothework.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.support.TestDatabase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 계정의 한살이를 실제 HTTP·보안 필터·DB로 끝까지 따라간다.
 *
 * <p>identity를 옮기며 바뀐 것들이 한 번에 맞물리는지 보는 테스트다: shared가 토큰을 검증하고 identity가 주체를
 * 채우는지, 발급한 토큰으로 다른 서비스(laborqa)의 API를 쓸 수 있는지, 탈퇴하면 그 토큰이 바로 막히고 laborqa가
 * 이벤트를 받아 이력을 지우는지. 메일 서버만 가짜로 두고 인증 링크의 토큰은 DB에서 읽는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class AccountLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void signUpVerifyLogInUseAnotherServiceRotateTokensAndWithdraw() throws Exception {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String email = "flow" + suffix + "@albam.dev";
        String phone = "010-" + suffix + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);

        // 가입
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Test1234!","passwordConfirm":"Test1234!","name":"흐름",
                                 "phone":"%s","birthDate":"1995-03-01","agreedToTerms":true}
                                """.formatted(email, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = ((Number) JsonPath.read(signup.getResponse().getContentAsString(), "$.data")).longValue();

        // 인증 전에는 로그인이 막히고, 프론트가 재발송 안내를 띄울 수 있도록 code가 따로 나간다
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Test1234!\"}".formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        // 메일 링크 열기 → 프론트 로그인 화면으로 이동
        String verifyToken = jdbcClient.sql("SELECT token FROM tw_identity.email_tokens WHERE user_id = ?")
                .param(userId).query(String.class).single();
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/login?verified=success"));

        // 로그인: 액세스 토큰은 본문, 리프레시 토큰은 HttpOnly 쿠키
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Test1234!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileCompleted").value(true))
                .andReturn();
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        Cookie refreshCookie = login.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        // 내 정보
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.provider").value("LOCAL"));
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // 같은 토큰으로 다른 서비스(laborqa)를 쓴다. @CurrentUserId가 identity가 채운 주체로 동작한다
        mockMvc.perform(post("/api/v1/labor-qa/sessions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());
        assertThat(laborQaSessionsOf(userId)).isEqualTo(1);

        // 리프레시 토큰 회전: 새 쿠키를 받고, 쓴 쿠키는 다시 쓸 수 없다
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(refreshed.getResponse().getCookie("refreshToken").getValue()).isNotEqualTo(refreshCookie.getValue());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isBadRequest());

        // 탈퇴하면 만료 전인 액세스 토큰도 바로 막히고, laborqa가 이벤트를 받아 이력을 지운다
        mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        await().atMost(Duration.ofSeconds(5)).until(() -> laborQaSessionsOf(userId) == 0);
    }

    private int laborQaSessionsOf(long userId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tw_laborqa.labor_qa_sessions WHERE user_id = ?")
                .param(userId).query(Integer.class).single();
    }
}
