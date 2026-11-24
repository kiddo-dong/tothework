package com.tothework.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.tothework.chat.message.ChatMessageService;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.store.StorePurgeJob;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestSchedulerLocks;
import com.tothework.support.TestStores;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 매장 채팅방은 매장과 함께 생기고 함께 지워진다. 방이 없던 기존 매장은 앱이 뜰 때 채운다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabase.class)
class ChatRoomLifecycleTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private ApplicationContext context;
    @Autowired private ChatMessageService chatMessageService;
    @Autowired private StorePurgeJob storePurgeJob;
    @MockitoBean private JavaMailSender mailSender;

    private Account owner;
    private long storeId;

    @BeforeEach
    void setUp() throws Exception {
        owner = new TestAccounts(mockMvc, jdbcClient).create("사장");
        storeId = new TestStores(mockMvc, jdbcClient).open(owner);
    }

    /** 매장 생성 요청이 끝난 시점에 방이 이미 있다(같은 트랜잭션). 비동기로 만들면 이 시점에 없을 수 있다. */
    @Test
    void creatingAStoreCreatesItsRoomInTheSameTransaction() {
        assertThat(roomsOf(storeId)).isEqualTo(1);
        assertThat(chatMessageService.send(storeId, owner.userId(), "첫 메시지").id()).isPositive();
    }

    /** 방이 생기기 전에 만들어진 매장을 흉내 낸다. 앱이 뜰 때 도는 채우기가 방을 만들고, 다시 돌아도 하나뿐이다. */
    @Test
    void backfillCreatesMissingRoomsOnceForExistingStores() throws Exception {
        jdbcClient.sql("DELETE FROM tw_chat.chat_rooms WHERE store_id = ?").param(storeId).update();
        assertThatThrownBy(() -> chatMessageService.send(storeId, owner.userId(), "방 없음"))
                .isInstanceOf(NotFoundException.class);

        ApplicationRunner backfill = context.getBean("chatRoomBackfill", ApplicationRunner.class);
        backfill.run(new DefaultApplicationArguments());
        backfill.run(new DefaultApplicationArguments());

        assertThat(roomsOf(storeId)).isEqualTo(1);
        assertThat(chatMessageService.send(storeId, owner.userId(), "이제 됨").id()).isPositive();
    }

    @Test
    void purgingTheStoreRemovesItsRoomAndMessages() {
        chatMessageService.send(storeId, owner.userId(), "곧 지워질 메시지");
        jdbcClient.sql("UPDATE tw_store.stores SET deleted_at = NOW() - INTERVAL 91 DAY WHERE id = ?")
                .param(storeId).update();

        TestSchedulerLocks.releaseAll(jdbcClient);
        storePurgeJob.purgeExpiredStores();

        await().atMost(Duration.ofSeconds(5)).until(() -> roomsOf(storeId) == 0 && messagesOf(storeId) == 0);
    }

    private long roomsOf(long storeId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tw_chat.chat_rooms WHERE store_id = ?").param(storeId)
                .query(Long.class).single();
    }

    private long messagesOf(long storeId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM tw_chat.chat_messages WHERE store_id = ?").param(storeId)
                .query(Long.class).single();
    }
}
