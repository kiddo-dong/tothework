package com.tothework.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.shared.storage.S3Uploader;
import com.tothework.store.store.StorePurgeJob;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * operations의 여섯 기능을 실제 API로 돌린다. 작성자 이름은 store·identity에서 채워 오고, 저장은 Spring Data JDBC
 * 애그리거트(자식 컬렉션 포함)로 하므로 매핑이 실제 테이블과 맞는지가 여기서 드러난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class OperationsFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private StorePurgeJob storePurgeJob;
    @MockitoBean private JavaMailSender mailSender;
    /** 매뉴얼 삭제가 S3 파일을 지우러 나가지 않게 한다. */
    @MockitoBean private S3Uploader s3Uploader;

    private Account owner;
    private Account staff;
    private Account colleague;
    private long storeId;
    private long staffMemberId;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(mockMvc, jdbcClient);
        TestStores stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        staff = accounts.create("알바");
        colleague = accounts.create("동료");
        storeId = stores.open(owner);
        staffMemberId = stores.join(storeId, owner, staff);
        stores.join(storeId, owner, colleague);
        base = "/api/v1/stores/" + storeId;
    }

    @Test
    void checklistIsCheckedOnceEvenWhenTappedRepeatedly() throws Exception {
        String items = body(perform(post(base + "/checklist-items/bulk"), owner, """
                {"items":[{"type":"OPEN","content":"포스기 시재 확인"},{"type":"OPEN","content":"매장 청소"},
                          {"type":"CLOSE","content":"마감 정산"}]}""").andExpect(status().isCreated()));
        long first = id(items, "$.data[0].id");
        assertThat((List<Integer>) JsonPath.read(items, "$.data[*].displayOrder")).containsExactly(0, 1, 0);

        int okCount = race(8, () -> perform(post(base + "/checklist-items/" + first + "/check"), staff, null)
                .andReturn().getResponse().getStatus() == 200);
        assertThat(okCount).isEqualTo(8);
        assertThat(count("checklist_completions", "item_id", first)).isEqualTo(1);

        // 구분은 MySQL ENUM 정의 순서(CLOSE, OPEN)로 정렬된다. 기존 스키마도 같아서 마감 항목이 먼저 온다.
        String entry = "$.data[?(@.itemId == " + first + ")]";
        perform(get(base + "/checklist"), staff, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("CLOSE"))
                .andExpect(jsonPath(entry + ".checked").value(true))
                .andExpect(jsonPath(entry + ".checkedByName").value("알바"))
                .andExpect(jsonPath("$.data[?(@.checked == true)]").value(org.hamcrest.Matchers.hasSize(1)));

        perform(delete(base + "/checklist-items/" + first + "/check"), staff, null).andExpect(status().isOk());
        perform(get(base + "/checklist"), staff, null).andExpect(jsonPath(entry + ".checked").value(false));
        perform(post(base + "/checklist-items"), staff, "{\"type\":\"OPEN\",\"content\":\"x\"}")
                .andExpect(status().isForbidden());
        perform(delete(base + "/checklist-items/" + first), owner, null).andExpect(status().isOk());
    }

    @Test
    void noticeReadsAreCountedOncePerMember() throws Exception {
        long noticeId = id(body(perform(post(base + "/notices"), owner, "{\"title\":\"공지\",\"content\":\"내용\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorName").value("사장"))), "$.data.id");
        perform(post(base + "/notices"), staff, "{\"title\":\"t\",\"content\":\"c\"}").andExpect(status().isForbidden());

        perform(get(base + "/notices"), staff, null)
                .andExpect(jsonPath("$.data[0].readByMe").value(false))
                .andExpect(jsonPath("$.data[0].readCount").value(0));
        perform(post(base + "/notices/" + noticeId + "/read"), staff, null).andExpect(status().isOk());
        perform(post(base + "/notices/" + noticeId + "/read"), staff, null).andExpect(status().isOk());
        perform(get(base + "/notices"), staff, null)
                .andExpect(jsonPath("$.data[0].readByMe").value(true))
                .andExpect(jsonPath("$.data[0].readCount").value(1))
                .andExpect(jsonPath("$.data[0].authorName").value("사장"));

        String reads = body(perform(get(base + "/notices/" + noticeId + "/reads"), owner, null)
                .andExpect(status().isOk()));
        assertThat((List<Object>) JsonPath.read(reads, "$.data")).hasSize(3);
        assertThat((List<Object>) JsonPath.read(reads, "$.data[?(@.storeMemberId == " + staffMemberId + ")].readAt"))
                .singleElement().isNotNull();

        perform(delete(base + "/notices/" + noticeId), owner, null).andExpect(status().isOk());
        assertThat(count("notice_reads", "notice_id", noticeId)).isZero();
    }

    @Test
    void handoverNotesAreEditableOnlyByTheAuthorOrAManager() throws Exception {
        long noteId = id(body(perform(post(base + "/handover-notes"), staff, "{\"content\":\"우유 떨어짐\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorName").value("알바"))
                .andExpect(jsonPath("$.data.authorMemberId").value(staffMemberId))
                .andExpect(jsonPath("$.data.workDate").value(LocalDate.now().toString()))), "$.data.id");

        perform(patch(base + "/handover-notes/" + noteId), colleague, "{\"content\":\"고침\"}")
                .andExpect(status().isForbidden());
        perform(patch(base + "/handover-notes/" + noteId), owner, "{\"content\":\"우유 2개 떨어짐\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("우유 2개 떨어짐"))
                .andExpect(jsonPath("$.data.authorName").value("알바"));
        perform(get(base + "/handover-notes").param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().toString()), colleague, null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].authorName").value("알바"));
        perform(delete(base + "/handover-notes/" + noteId), staff, null).andExpect(status().isOk());
    }

    @Test
    void manualsKeepTheirImagesInOrder() throws Exception {
        String keys = "[\"manual-images/" + storeId + "/a.png\",\"manual-images/" + storeId + "/b.png\"]";
        long manualId = id(body(perform(post(base + "/manuals"), owner,
                "{\"category\":\"조리\",\"title\":\"라떼\",\"content\":\"우유 200ml\",\"imageKeys\":" + keys + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorName").value("사장"))
                .andExpect(jsonPath("$.data.images[0].key").value("manual-images/" + storeId + "/a.png"))
                .andExpect(jsonPath("$.data.images[1].key").value("manual-images/" + storeId + "/b.png"))),
                "$.data.id");
        perform(post(base + "/manuals"), owner,
                "{\"category\":\"조리\",\"title\":\"x\",\"content\":\"x\",\"imageKeys\":[\"manual-images/999/a.png\"]}")
                .andExpect(status().isForbidden());

        perform(get(base + "/manuals"), staff, null)
                .andExpect(jsonPath("$.data[0].title").value("라떼"))
                .andExpect(jsonPath("$.data[0].images").doesNotExist());
        perform(get(base + "/manuals/" + manualId), staff, null)
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.authorName").value("사장"));
        perform(delete(base + "/manuals/" + manualId), owner, null).andExpect(status().isOk());
        assertThat(count("manual_images", "manual_id", manualId)).isZero();
        org.mockito.Mockito.verify(s3Uploader).delete("manual-images/" + storeId + "/a.png");
        org.mockito.Mockito.verify(s3Uploader).delete("manual-images/" + storeId + "/b.png");
    }

    @Test
    void supplierItemsKeepTheirIdsWhenEdited() throws Exception {
        long supplierId = id(body(perform(post(base + "/suppliers"), owner,
                "{\"name\":\"우유상사\",\"category\":\"유제품\"}").andExpect(status().isCreated())), "$.data.id");
        long itemId = id(body(perform(post(base + "/suppliers/" + supplierId + "/items"), owner,
                "{\"name\":\"우유\",\"spec\":\"1L\",\"weeklyQuantities\":{\"MONDAY\":\"10개\",\"THURSDAY\":\"2박스\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.weeklyQuantities.MONDAY").value("10개"))), "$.data.id");

        perform(patch(base + "/suppliers/items/" + itemId), owner,
                "{\"name\":\"우유\",\"spec\":\"1L\",\"weeklyQuantities\":{\"FRIDAY\":\"5개\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(itemId))
                .andExpect(jsonPath("$.data.weeklyQuantities.FRIDAY").value("5개"))
                .andExpect(jsonPath("$.data.weeklyQuantities.MONDAY").doesNotExist());
        perform(get(base + "/suppliers"), owner, null)
                .andExpect(jsonPath("$.data[0].items[0].id").value(itemId))
                .andExpect(jsonPath("$.data[0].items[0].weeklyQuantities.FRIDAY").value("5개"));
        perform(get(base + "/suppliers"), staff, null).andExpect(status().isForbidden());

        perform(delete(base + "/suppliers/" + supplierId), owner, null).andExpect(status().isOk());
        assertThat(count("supplier_items", "supplier_id", supplierId)).isZero();
    }

    @Test
    void deletingAnIngredientRemovesItFromRecipesAndKeepsTheRestInOrder() throws Exception {
        long milk = ingredient("우유", 3000, 1000);
        long beans = ingredient("원두", 20000, 1000);
        long syrup = ingredient("시럽", 5000, 500);
        long menuId = id(body(perform(post(base + "/menus"), owner, """
                {"name":"라떼","sellingPrice":5000,"category":"커피",
                 "recipe":[{"ingredientId":%d,"amount":200},{"ingredientId":%d,"amount":20},{"ingredientId":%d,"amount":10}]}"""
                .formatted(milk, beans, syrup))
                .andExpect(status().isCreated())
                // 우유 600 + 원두 400 + 시럽 100
                .andExpect(jsonPath("$.data.ingredientCost").value(1100))
                .andExpect(jsonPath("$.data.profit").value(3900))), "$.data.id");

        perform(delete(base + "/menu-ingredients/" + beans), owner, null).andExpect(status().isOk());

        perform(get(base + "/menus/" + menuId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipe.length()").value(2))
                .andExpect(jsonPath("$.data.recipe[0].ingredientName").value("우유"))
                .andExpect(jsonPath("$.data.recipe[1].ingredientName").value("시럽"))
                .andExpect(jsonPath("$.data.ingredientCost").value(700));
        perform(get(base + "/menus"), owner, null)
                .andExpect(jsonPath("$.data[0].recipe.length()").value(2))
                .andExpect(jsonPath("$.data[0].ingredientCost").value(700));
        perform(post(base + "/menus"), owner,
                "{\"name\":\"x\",\"sellingPrice\":1,\"category\":\"x\",\"recipe\":[{\"ingredientId\":999999,\"amount\":1}]}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("재료를 찾을 수 없습니다: 999999"));
    }

    @Test
    void purgingTheStoreRemovesAllOperationsData() throws Exception {
        perform(post(base + "/checklist-items"), owner, "{\"type\":\"OPEN\",\"content\":\"청소\"}");
        perform(post(base + "/notices"), owner, "{\"title\":\"공지\",\"content\":\"내용\"}");
        perform(post(base + "/handover-notes"), staff, "{\"content\":\"메모\"}");
        perform(post(base + "/manuals"), owner, "{\"category\":\"조리\",\"title\":\"t\",\"content\":\"c\"}");
        perform(post(base + "/suppliers"), owner, "{\"name\":\"상사\",\"category\":\"식자재\"}");
        long milk = ingredient("우유", 3000, 1000);
        perform(post(base + "/menus"), owner, "{\"name\":\"라떼\",\"sellingPrice\":5000,\"category\":\"커피\","
                + "\"recipe\":[{\"ingredientId\":" + milk + ",\"amount\":200}]}").andExpect(status().isCreated());

        jdbcClient.sql("UPDATE tw_store.stores SET deleted_at = NOW() - INTERVAL 91 DAY WHERE id = ?")
                .param(storeId).update();
        storePurgeJob.purgeExpiredStores();

        List<String> tables = List.of("checklist_items", "notices", "handover_notes", "manuals", "suppliers",
                "menu_ingredients", "store_menus");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> tables.stream().allMatch(table -> count(table, "store_id", storeId) == 0));
    }

    private long ingredient(String name, int price, double packageQty) throws Exception {
        return id(body(perform(post(base + "/menu-ingredients"), owner, """
                {"name":"%s","price":%d,"packageQty":%s,"unit":"ML","lossRate":0,"category":"재료"}"""
                .formatted(name, price, packageQty)).andExpect(status().isCreated())), "$.data.id");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Account as, String json) throws Exception {
        request.header("Authorization", as.bearer());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private int count(String table, String column, long value) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tw_operations." + table + " WHERE " + column + " = ?")
                .param(value).query(Integer.class).single();
    }

    private interface Attempt {
        boolean run() throws Exception;
    }

    private static int race(int attempts, Attempt attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return attempt.run();
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();
        return succeeded;
    }
}
