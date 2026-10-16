package com.tothework.support;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.support.TestAccounts.Account;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** 통합 테스트용 매장. 실제 API로 매장을 열고 직원을 초대 코드로 합류시킨다. */
public final class TestStores {

    /** 매일 08~23시 영업. 스케줄 검증에 영업시간이 걸리지 않게 넉넉히 둔다. */
    private static final String ALL_WEEK_HOURS = """
            {"MONDAY":{"openTime":"08:00","closeTime":"23:00","closed":false},
             "TUESDAY":{"openTime":"08:00","closeTime":"23:00","closed":false},
             "WEDNESDAY":{"openTime":"08:00","closeTime":"23:00","closed":false},
             "THURSDAY":{"openTime":"08:00","closeTime":"23:00","closed":false},
             "FRIDAY":{"openTime":"08:00","closeTime":"23:00","closed":false},
             "SATURDAY":{"openTime":"08:00","closeTime":"23:00","closed":false},
             "SUNDAY":{"openTime":"08:00","closeTime":"23:00","closed":false}}""";

    private final MockMvc mockMvc;
    private final JdbcClient jdbcClient;

    public TestStores(MockMvc mockMvc, JdbcClient jdbcClient) {
        this.mockMvc = mockMvc;
        this.jdbcClient = jdbcClient;
    }

    public long open(Account owner) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"테스트 매장\",\"businessHours\":" + ALL_WEEK_HOURS + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 초대 코드로 신청하고 승인한다. 합류 이벤트로 고용 조건이 만들어질 때까지 기다린 뒤 memberId를 돌려준다. */
    public long join(long storeId, Account owner, Account worker) throws Exception {
        String code = JsonPath.read(mockMvc.perform(get("/api/v1/stores/" + storeId + "/invite-code")
                        .header("Authorization", owner.bearer()))
                .andReturn().getResponse().getContentAsString(), "$.data.code");
        String request = mockMvc.perform(post("/api/v1/join-requests").header("Authorization", worker.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long requestId = ((Number) JsonPath.read(request, "$.data.id")).longValue();
        mockMvc.perform(post("/api/v1/stores/" + storeId + "/join-requests/" + requestId + "/approve")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STAFF\"}"))
                .andExpect(status().isOk());
        long memberId = memberIdOf(storeId, worker);
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbcClient
                .sql("SELECT COUNT(*) FROM tw_workforce.employment_terms WHERE member_id = ?")
                .param(memberId).query(Integer.class).single() == 1);
        return memberId;
    }

    public long memberIdOf(long storeId, Account account) {
        return jdbcClient.sql("SELECT id FROM tw_store.store_members WHERE store_id = ? AND user_id = ?")
                .params(storeId, account.userId()).query(Long.class).single();
    }
}
