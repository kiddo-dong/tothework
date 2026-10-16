package com.tothework.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tothework.support.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 없이 열린 경로 아래의 없는 주소는 404다. 기본 설정에서는 API 문서가 꺼져 있고 Actuator는 별도 관리 포트에만
 * 있으므로, 앱 포트로 그 주소를 부르면 "없음"이어야 한다(예전에는 500이었다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class UnknownPathTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void docsAreClosedByDefaultAndActuatorIsNotOnTheAppPort() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 경로를 찾을 수 없습니다."));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }
}
