package com.tothework.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tothework.chat.message.ChatMessageService;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 이전 메시지 조회(REST). id 내림차순 커서 페이지네이션과 소속 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class StoreChatHistoryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ChatMessageService chatMessageService;
    @MockitoBean private JavaMailSender mailSender;

    private Account owner;
    private Account staff;
    private long storeId;
    private final List<Long> sentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        TestAccounts accounts = new TestAccounts(mockMvc, jdbcClient);
        TestStores stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        staff = accounts.create("알바");
        storeId = stores.open(owner);
        stores.join(storeId, owner, staff);
        for (int i = 1; i <= 5; i++) {
            long sender = i % 2 == 0 ? owner.userId() : staff.userId();
            sentIds.add(chatMessageService.send(storeId, sender, "메시지 " + i).id());
        }
    }

    @Test
    void pagesGoFromNewestToOldestUsingTheLastIdAsTheCursor() throws Exception {
        List<Long> seen = new ArrayList<>();
        Long before = null;
        while (true) {
            List<Number> page = JsonPath.read(page(before, 2).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "$.data[*].id");
            if (page.isEmpty()) {
                break;
            }
            assertThat(page).hasSizeLessThanOrEqualTo(2);
            page.forEach(id -> seen.add(id.longValue()));
            before = page.getLast().longValue();
        }
        assertThat(seen).containsExactlyElementsOf(sentIds.reversed());
    }

    @Test
    void deletedMessagesKeepTheirPlaceButHideTheContent() throws Exception {
        long oldest = sentIds.getFirst();
        chatMessageService.delete(storeId, staff.userId(), oldest);

        page(null, 30)
                .andExpect(jsonPath("$.data[4].id").value(oldest))
                .andExpect(jsonPath("$.data[4].deleted").value(true))
                .andExpect(jsonPath("$.data[4].content").value("삭제된 메시지입니다"))
                .andExpect(jsonPath("$.data[0].senderName").value("알바"));
    }

    @Test
    void outsidersAndBadSizesAreRejected() throws Exception {
        Account stranger = new TestAccounts(mockMvc, jdbcClient).create("외부인");
        mockMvc.perform(get("/api/v1/stores/" + storeId + "/chat/messages").header("Authorization", stranger.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/stores/" + storeId + "/chat/messages"))
                .andExpect(status().isUnauthorized());
        page(null, 101).andExpect(status().isBadRequest());
    }

    private ResultActions page(Long before, int size) throws Exception {
        var request = get("/api/v1/stores/" + storeId + "/chat/messages").header("Authorization", staff.bearer())
                .param("size", String.valueOf(size));
        if (before != null) {
            request.param("before", String.valueOf(before));
        }
        return mockMvc.perform(request);
    }
}
