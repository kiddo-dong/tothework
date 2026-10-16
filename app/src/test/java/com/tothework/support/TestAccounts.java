package com.tothework.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 통합 테스트용 계정. 실제 API로 가입 → 메일 인증 → 로그인까지 마친 사용자를 만든다. 인증 링크의 토큰은 메일 대신
 * DB에서 읽는다. 로컬 DB에 데이터가 남으므로 이메일·전화번호는 매번 새로 만든다.
 */
public final class TestAccounts {

    public record Account(long userId, String email, String accessToken) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private final MockMvc mockMvc;
    private final JdbcClient jdbcClient;

    public TestAccounts(MockMvc mockMvc, JdbcClient jdbcClient) {
        this.mockMvc = mockMvc;
        this.jdbcClient = jdbcClient;
    }

    public Account create(String name) throws Exception {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String email = "it" + System.nanoTime() + "@albam.dev";
        String phone = "010-" + random.nextInt(1000, 10000) + "-" + random.nextInt(1000, 10000);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Test1234!","passwordConfirm":"Test1234!","name":"%s",
                                 "phone":"%s","birthDate":"1995-03-01","agreedToTerms":true}
                                """.formatted(email, name, phone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long userId = ((Number) JsonPath.read(signup, "$.data")).longValue();
        String token = jdbcClient.sql("SELECT token FROM tw_identity.email_tokens WHERE user_id = ?")
                .param(userId).query(String.class).single();
        mockMvc.perform(get("/api/v1/auth/verify-email").param("token", token)).andExpect(status().isFound());
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Test1234!\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Account(userId, email, JsonPath.read(login, "$.data.accessToken"));
    }
}
