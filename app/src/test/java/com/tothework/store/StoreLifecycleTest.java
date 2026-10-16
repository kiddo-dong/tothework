package com.tothework.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.store.store.StorePurgeJob;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 매장의 한살이를 실제 HTTP·보안·MySQL로 따라간다. 이번 설계의 핵심이 맞물리는지 본다.
 *
 * <ul>
 *   <li>멤버 정보가 store(소속)와 workforce(고용 조건) 두 스키마에 나뉘어 저장되고, bff가 하나의 응답으로 합친다
 *   <li>한쪽 검증이 실패하면 다른 쪽도 바뀌지 않는다 (트랜잭션 없이, 검증을 먼저 끝내고 적용한다)
 *   <li>합류 이벤트로 고용 조건이 생기고, 완전 삭제 이벤트로 지워진다
 *   <li>삭제된 매장은 모든 경로에서 사라진다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class StoreLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private StorePurgeJob storePurgeJob;
    @MockitoBean private JavaMailSender mailSender;

    private Account owner;
    private Account staff;

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        staff = accounts.create("알바");
    }

    @Test
    void openJoinManageMembersDeleteAndPurge() throws Exception {
        // 매장 개설: 개설자는 OWNER
        long storeId = idOf(perform(post("/api/v1/stores"), owner, """
                {"name":"한살이 카페","category":"CAFE","smallBusiness":true,
                 "businessHours":{"MONDAY":{"openTime":"09:00","closeTime":"22:00","closed":false}}}
                """).andExpect(status().isCreated()));
        perform(get("/api/v1/stores/" + storeId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smallBusiness").value(true))
                .andExpect(jsonPath("$.data.businessHours.MONDAY.closed").value(false));
        perform(get("/api/v1/stores"), owner, null).andExpect(jsonPath("$.data[0].myRole").value("OWNER"));

        // 초대 코드로 가입 신청 → 승인
        String code = JsonPath.read(perform(get("/api/v1/stores/" + storeId + "/invite-code"), owner, null)
                .andReturn().getResponse().getContentAsString(), "$.data.code");
        long requestId = idOf(perform(post("/api/v1/join-requests"), staff, "{\"code\":\"%s\"}".formatted(code))
                .andExpect(status().isCreated()));
        perform(post("/api/v1/stores/" + storeId + "/join-requests/" + requestId + "/approve"), owner,
                "{\"role\":\"STAFF\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.userName").value("알바"));

        // 합류 이벤트를 받은 workforce가 기본 고용 조건을 만든다
        long staffMemberId = jdbcClient.sql("SELECT id FROM tw_store.store_members WHERE store_id = ? AND user_id = ?")
                .params(storeId, staff.userId()).query(Long.class).single();
        await().atMost(Duration.ofSeconds(5)).until(() -> wageOf(staffMemberId) != null);
        assertThat(wageOf(staffMemberId)).isZero();

        // 멤버 목록: 세 서비스의 값을 bff가 합친다
        perform(get("/api/v1/stores/" + storeId + "/members"), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == %d)].userName".formatted(staffMemberId)).value("알바"))
                .andExpect(jsonPath("$.data[?(@.id == %d)].hourlyWage".formatted(staffMemberId)).value(0));

        // 멤버 수정: 소속은 tw_store, 고용 조건은 tw_workforce에 저장되고 응답은 하나로 나간다
        perform(patch("/api/v1/stores/" + storeId + "/members/" + staffMemberId), owner, """
                {"role":"MANAGER","title":"홀팀장","hourlyWage":12000,"taxMode":"WITHHOLDING_3_3",
                 "availableDays":["MONDAY"],"weeklyHolidayDay":"SUNDAY"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.title").value("홀팀장"))
                .andExpect(jsonPath("$.data.hourlyWage").value(12000))
                .andExpect(jsonPath("$.data.taxMode").value("WITHHOLDING_3_3"))
                .andExpect(jsonPath("$.data.userEmail").value(staff.email()));
        assertThat(roleOf(staffMemberId)).isEqualTo("MANAGER");
        assertThat(wageOf(staffMemberId)).isEqualTo(12000);

        // 한쪽(시급) 검증이 실패하면 다른 쪽(역할)도 바뀌지 않는다
        perform(patch("/api/v1/stores/" + storeId + "/members/" + staffMemberId), owner,
                "{\"role\":\"STAFF\",\"hourlyWage\":5000}")
                .andExpect(status().isBadRequest());
        assertThat(roleOf(staffMemberId)).isEqualTo("MANAGER");
        assertThat(wageOf(staffMemberId)).isEqualTo(12000);

        // 매니저가 된 직원은 자기 시급을 올릴 수 없다
        perform(patch("/api/v1/stores/" + storeId + "/members/" + staffMemberId), staff, "{\"hourlyWage\":50000}")
                .andExpect(status().isForbidden());
        assertThat(wageOf(staffMemberId)).isEqualTo(12000);

        // 근무 가능 요일은 본인이 바꾼다
        perform(patch("/api/v1/stores/" + storeId + "/members/me/available-days"), staff,
                "{\"availableDays\":[\"TUESDAY\",\"FRIDAY\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableDays.length()").value(2))
                .andExpect(jsonPath("$.data.role").value("MANAGER"));

        // 활동 중인 매장이 있으면 탈퇴할 수 없다 (store가 identity의 WithdrawalGuard를 구현)
        perform(delete("/api/v1/users/me"), staff, null).andExpect(status().isConflict());

        // 매장 삭제: 유예기간 동안 모든 경로에서 사라진다
        perform(delete("/api/v1/stores/" + storeId).param("confirmName", "한살이 카페"), owner, null)
                .andExpect(status().isOk());
        perform(get("/api/v1/stores/" + storeId), owner, null).andExpect(status().isNotFound());
        perform(get("/api/v1/stores/" + storeId + "/members"), owner, null).andExpect(status().isNotFound());
        perform(get("/api/v1/stores"), staff, null).andExpect(jsonPath("$.data.length()").value(0));
        perform(post("/api/v1/join-requests"), staff, "{\"code\":\"%s\"}".formatted(code))
                .andExpect(status().isNotFound());

        // 유예기간이 지나면 완전히 지우고, 이벤트를 받은 workforce가 고용 조건을 지운다
        jdbcClient.sql("UPDATE tw_store.stores SET deleted_at = NOW() - INTERVAL 91 DAY WHERE id = ?")
                .param(storeId).update();
        storePurgeJob.purgeExpiredStores();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM tw_store.store_members WHERE store_id = ?")
                .param(storeId).query(Integer.class).single()).isZero();
        await().atMost(Duration.ofSeconds(5)).until(() -> wageOf(staffMemberId) == null);
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Account as, String body) throws Exception {
        request.header("Authorization", as.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String roleOf(long memberId) {
        return jdbcClient.sql("SELECT role FROM tw_store.store_members WHERE id = ?")
                .param(memberId).query(String.class).single();
    }

    private Integer wageOf(long memberId) {
        return jdbcClient.sql("SELECT hourly_wage FROM tw_workforce.employment_terms WHERE member_id = ?")
                .param(memberId).query(Integer.class).optional().orElse(null);
    }
}
