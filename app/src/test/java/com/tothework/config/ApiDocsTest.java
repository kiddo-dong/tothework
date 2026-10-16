package com.tothework.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.support.TestDatabase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** SWAGGER_ENABLED=true면 문서가 로그인 없이 열리고, 모든 서비스의 API가 한 문서에 실린다. */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class ApiDocsTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void docsAreOpenWithoutLoginAndCoverEveryService() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Map<String, Object>> paths = JsonPath.read(docs, "$.paths");
        int operations = paths.values().stream().mapToInt(Map::size).sum();
        assertThat(operations).as("문서에 실린 API 수 (기존 백엔드와 같은 117개)").isEqualTo(117);
        assertThat(paths).containsKeys("/api/v1/auth/login", "/api/v1/stores/{storeId}/home",
                "/api/v1/stores/{storeId}/payroll", "/api/v1/stores/{storeId}/menus", "/api/v1/labor-qa/sessions");
    }
}
