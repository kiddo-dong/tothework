package com.tothework.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.shared.storage.S3Uploader;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import java.time.LocalDate;
import java.util.List;
import net.ttddyy.dsproxy.QueryCountHolder;
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

/**
 * operations 목록 API의 쿼리 수가 행 수에 따라 늘지 않는지 본다. 작성자 이름(store·identity 조회)과 자식 컬렉션
 * (매뉴얼 이미지·요일별 발주량·레시피 줄)이 행마다 따로 조회되기 쉬운 곳들이다. 방식은 {@link QueryCountTest}와 같다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestDatabase.class, QueryCountTest.CountingDataSource.class})
class OperationsQueryCountTest {

    private static final int MORE = 3;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @MockitoBean private JavaMailSender mailSender;
    @MockitoBean private S3Uploader s3Uploader;

    private TestAccounts accounts;
    private TestStores stores;
    private Account owner;
    private long storeId;
    private String base;
    private long checklistItemId;
    private long noticeId;
    private long supplierId;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new TestAccounts(mockMvc, jdbcClient);
        stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        storeId = stores.open(owner);
        base = "/api/v1/stores/" + storeId;
        checklistItemId = idOf(send(owner, base + "/checklist-items", "{\"type\":\"OPEN\",\"content\":\"청소\"}"));
        noticeId = idOf(send(owner, base + "/notices", "{\"title\":\"공지\",\"content\":\"내용\"}"));
        supplierId = idOf(send(owner, base + "/suppliers", "{\"name\":\"상사\",\"category\":\"식자재\"}"));
        addRows();
    }

    @Test
    void listsDoNotGrowWithTheNumberOfRows() throws Exception {
        String today = LocalDate.now().toString();
        List<String> urls = List.of(
                base + "/checklist",
                base + "/notices",
                base + "/notices/" + noticeId + "/reads",
                base + "/handover-notes?from=" + today + "&to=" + today,
                base + "/manuals",
                base + "/suppliers",
                base + "/menu-ingredients",
                base + "/menus");
        List<Integer> before = urls.stream().map(this::queriesOf).toList();
        for (int i = 0; i < MORE; i++) {
            addRows();
        }
        List<Integer> after = urls.stream().map(this::queriesOf).toList();
        for (int i = 0; i < urls.size(); i++) {
            assertThat(after.get(i)).as("%s의 쿼리 수", urls.get(i)).isEqualTo(before.get(i));
        }
    }

    /** 직원 한 명이 합류해 체크·공지 확인·인수인계를 남기고, 사장이 공지·매뉴얼·발주 품목·메뉴를 하나씩 더한다. */
    private void addRows() throws Exception {
        Account worker = accounts.create("알바");
        stores.join(storeId, owner, worker);
        send(worker, base + "/checklist-items/" + checklistItemId + "/check", null);
        send(worker, base + "/notices/" + noticeId + "/read", null);
        send(worker, base + "/handover-notes", "{\"content\":\"메모\"}");
        send(owner, base + "/notices", "{\"title\":\"공지\",\"content\":\"내용\"}");
        send(owner, base + "/checklist-items", "{\"type\":\"CLOSE\",\"content\":\"마감\"}");
        send(owner, base + "/manuals", "{\"category\":\"조리\",\"title\":\"t\",\"content\":\"c\",\"imageKeys\":[\"manual-images/"
                + storeId + "/a.png\"]}");
        send(owner, base + "/suppliers/" + supplierId + "/items",
                "{\"name\":\"우유\",\"weeklyQuantities\":{\"MONDAY\":\"1개\"}}");
        long ingredient = idOf(send(owner, base + "/menu-ingredients",
                "{\"name\":\"우유\",\"price\":3000,\"packageQty\":1000,\"unit\":\"ML\",\"lossRate\":0,\"category\":\"재료\"}"));
        send(owner, base + "/menus", "{\"name\":\"라떼\",\"sellingPrice\":5000,\"category\":\"커피\","
                + "\"recipe\":[{\"ingredientId\":" + ingredient + ",\"amount\":200}]}");
    }

    private String send(Account as, String url, String json) throws Exception {
        var request = post(url).header("Authorization", as.bearer());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString();
    }

    private static long idOf(String body) {
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private int queriesOf(String url) {
        try {
            QueryCountHolder.clear();
            mockMvc.perform(get(url).header("Authorization", owner.bearer())).andExpect(status().isOk());
            return (int) QueryCountHolder.getGrandTotal().getTotal();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            QueryCountHolder.clear();
        }
    }
}
