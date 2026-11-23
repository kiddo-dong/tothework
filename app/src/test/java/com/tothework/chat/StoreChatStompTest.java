package com.tothework.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.tothework.support.TestAccounts;
import com.tothework.support.TestAccounts.Account;
import com.tothework.support.TestDatabase;
import com.tothework.support.TestStores;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 서버 포트에 WebSocket으로 붙어 STOMP 채팅을 돌린다.
 *
 * <p>프로토콜 수준 오류(인증·소속)는 ERROR 프레임과 함께 연결이 끊기고, 애플리케이션 수준 오류(남의 메시지 삭제)는
 * 보낸 사람의 개인 큐로만 가고 연결이 유지되는지를 본다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestDatabase.class)
@TestInstance(Lifecycle.PER_CLASS)
class StoreChatStompTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @LocalServerPort private int port;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcClient jdbcClient;
    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private StringRedisTemplate redisTemplate;
    @MockitoBean private JavaMailSender mailSender;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    private Account owner;
    private Account staff;
    private Account stranger;
    private long storeId;

    /** 계정과 매장은 클래스에서 한 번만 만든다. 테스트 사이의 연결·구독은 tearDown에서 정리를 기다린다. */
    @BeforeAll
    void setUpStore() throws Exception {
        TestAccounts accounts = new TestAccounts(mockMvc, jdbcClient);
        TestStores stores = new TestStores(mockMvc, jdbcClient);
        owner = accounts.create("사장");
        staff = accounts.create("알바");
        stranger = accounts.create("외부인");
        storeId = stores.open(owner);
        stores.join(storeId, owner, staff);
    }

    @BeforeEach
    void setUpClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // 본문은 바이트 그대로 주고받고 JSON은 테스트에서 직접 읽는다. 기본값은 octet-stream만 받는다.
        stompClient.setMessageConverter(new ByteArrayMessageConverter() {
            {
                addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
            }
        });
    }

    /** 앞 테스트의 구독이 서버에서 정리될 때까지 기다린다. 구독 수를 세는 검증이 섞이지 않게 한다. */
    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        sessions.clear();
        await().atMost(WAIT).until(() -> userRegistry.getUserCount() == 0);
    }

    @Test
    void connectRequiresAValidToken() throws Exception {
        Client valid = connect(owner.bearer());
        assertThat(valid.session.isConnected()).isTrue();

        Client missing = connectExpectingError(null);
        assertThat(missing.errors.poll(5, TimeUnit.SECONDS)).contains("인증이 필요합니다");

        Client forged = connectExpectingError("Bearer not-a-jwt");
        assertThat(forged.errors.poll(5, TimeUnit.SECONDS)).contains("인증");
    }

    @Test
    void onlyMembersCanSubscribeToAStoreChat() throws Exception {
        Client member = connect(staff.bearer());
        member.subscribe(topic());
        awaitSubscribers(topic(), 1);
        assertThat(member.session.isConnected()).isTrue();

        Client outsider = connect(stranger.bearer());
        outsider.subscribe(topic());
        assertThat(outsider.errors.poll(5, TimeUnit.SECONDS)).contains("멤버가 아닙니다");
        await().atMost(WAIT).until(() -> !outsider.session.isConnected());
        assertThat(subscribers(topic())).isEqualTo(1);
    }

    @Test
    void aMessageReachesEveryoneSubscribedToTheStore() throws Exception {
        Client ownerClient = connect(owner.bearer());
        Client staffClient = connect(staff.bearer());
        BlockingQueue<String> ownerInbox = ownerClient.subscribe(topic());
        BlockingQueue<String> staffInbox = staffClient.subscribe(topic());
        awaitSubscribers(topic(), 2);

        staffClient.send("chat.send", "{\"content\":\"오늘 마감 제가 할게요\"}");

        for (BlockingQueue<String> inbox : List.of(ownerInbox, staffInbox)) {
            String event = inbox.poll(5, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("CREATED");
            assertThat((String) JsonPath.read(event, "$.message.content")).isEqualTo("오늘 마감 제가 할게요");
            assertThat((String) JsonPath.read(event, "$.message.senderName")).isEqualTo("알바");
            assertThat(((Number) JsonPath.read(event, "$.message.senderId")).longValue()).isEqualTo(staff.userId());
        }
    }

    @Test
    void deletingSomeoneElsesMessageOnlyTellsTheRequesterAndKeepsTheConnection() throws Exception {
        Client ownerClient = connect(owner.bearer());
        Client staffClient = connect(staff.bearer());
        BlockingQueue<String> ownerInbox = ownerClient.subscribe(topic());
        ownerClient.subscribe("/user/queue/errors", ownerClient.errors);
        BlockingQueue<String> staffErrors = staffClient.subscribe("/user/queue/errors");
        awaitSubscribers(topic(), 1);
        awaitSubscribers("/user/queue/errors", 2);

        staffClient.send("chat.send", "{\"content\":\"알바가 쓴 글\"}");
        long messageId = ((Number) JsonPath.read(ownerInbox.poll(5, TimeUnit.SECONDS), "$.message.id")).longValue();

        ownerClient.send("chat.delete", "{\"messageId\":" + messageId + "}");

        String error = ownerClient.errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat((String) JsonPath.read(error, "$.code")).isEqualTo("FORBIDDEN");
        assertThat(staffErrors.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(ownerInbox.poll(500, TimeUnit.MILLISECONDS)).as("실패한 삭제는 방송되지 않는다").isNull();

        // 연결이 살아 있어 같은 세션으로 계속 보낼 수 있다.
        assertThat(ownerClient.session.isConnected()).isTrue();
        ownerClient.send("chat.send", "{\"content\":\"아직 연결돼 있어요\"}");
        assertThat((String) JsonPath.read(ownerInbox.poll(5, TimeUnit.SECONDS), "$.message.content"))
                .isEqualTo("아직 연결돼 있어요");
    }

    @Test
    void editAndDeleteAreBroadcastAsEvents() throws Exception {
        Client staffClient = connect(staff.bearer());
        BlockingQueue<String> inbox = staffClient.subscribe(topic());
        awaitSubscribers(topic(), 1);

        staffClient.send("chat.send", "{\"content\":\"원래\"}");
        long messageId = ((Number) JsonPath.read(inbox.poll(5, TimeUnit.SECONDS), "$.message.id")).longValue();

        staffClient.send("chat.edit", "{\"messageId\":" + messageId + ",\"content\":\"고침\"}");
        String updated = inbox.poll(5, TimeUnit.SECONDS);
        assertThat((String) JsonPath.read(updated, "$.type")).isEqualTo("UPDATED");
        assertThat((String) JsonPath.read(updated, "$.message.content")).isEqualTo("고침");

        staffClient.send("chat.delete", "{\"messageId\":" + messageId + "}");
        String deleted = inbox.poll(5, TimeUnit.SECONDS);
        assertThat((String) JsonPath.read(deleted, "$.type")).isEqualTo("DELETED");
        assertThat((String) JsonPath.read(deleted, "$.message.content")).isEqualTo("삭제된 메시지입니다");
    }

    /**
     * 다른 서버가 Redis에 발행한 이벤트도 이 서버의 구독자에게 간다. 서버가 2대일 때 서로 다른 서버에 붙은 사람끼리
     * 메시지를 주고받는 경로가 이것이다.
     */
    @Test
    void anEventPublishedByAnotherServerReachesSubscribersHere() throws Exception {
        Client staffClient = connect(staff.bearer());
        BlockingQueue<String> inbox = staffClient.subscribe(topic());
        awaitSubscribers(topic(), 1);

        redisTemplate.convertAndSend("chat:stores:" + storeId, """
                {"type":"CREATED","message":{"id":987654,"storeId":%d,"senderId":1,"senderName":"다른 서버",
                 "senderProfileImageUrl":null,"content":"app2에서 보냄","deleted":false,
                 "createdAt":"2026-11-23T09:30:00","updatedAt":"2026-11-23T09:30:00"}}""".formatted(storeId));

        String event = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat((String) JsonPath.read(event, "$.message.content")).isEqualTo("app2에서 보냄");
    }

    /** 로컬 전송은 Redis 리스너 한 곳에서만 한다. 발행하면서 직접 보내기도 하면 같은 서버의 사람이 두 번 받는다. */
    @Test
    void aMessageIsDeliveredOnceToEachSubscriber() throws Exception {
        Client staffClient = connect(staff.bearer());
        BlockingQueue<String> inbox = staffClient.subscribe(topic());
        awaitSubscribers(topic(), 1);

        staffClient.send("chat.send", "{\"content\":\"한 번만\"}");

        assertThat(inbox.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(inbox.poll(1, TimeUnit.SECONDS)).as("같은 이벤트가 두 번 오면 안 된다").isNull();
    }

    // ---------- 도우미 ----------

    private String topic() {
        return "/topic/stores/" + storeId + "/chat";
    }

    private int subscribers(String destination) {
        return userRegistry.findSubscriptions(subscription -> subscription.getDestination().equals(destination))
                .size();
    }

    /** SUBSCRIBE는 비동기라, 서버에 구독이 등록된 걸 확인한 뒤에 보낸다. */
    private void awaitSubscribers(String destination, int count) {
        await().atMost(WAIT).until(() -> subscribers(destination) == count);
    }

    private Client connect(String bearer) throws Exception {
        Client client = new Client();
        client.session = client.connect(bearer).get(5, TimeUnit.SECONDS);
        sessions.add(client.session);
        return client;
    }

    /** ERROR 프레임을 받으면 연결이 끊기므로 세션이 끝까지 만들어지지 않을 수 있다. 오류 문구만 모은다. */
    private Client connectExpectingError(String bearer) {
        Client client = new Client();
        client.connect(bearer);
        return client;
    }

    private class Client extends StompSessionHandlerAdapter {

        StompSession session;
        /** ERROR 프레임의 message 헤더, 또는 /user/queue/errors로 받은 본문. */
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        CompletableFuture<StompSession> connect(String bearer) {
            StompHeaders headers = new StompHeaders();
            if (bearer != null) {
                headers.add("Authorization", bearer);
            }
            return stompClient.connectAsync("ws://127.0.0.1:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                    this);
        }

        BlockingQueue<String> subscribe(String destination) {
            return subscribe(destination, new LinkedBlockingQueue<>());
        }

        BlockingQueue<String> subscribe(String destination, BlockingQueue<String> inbox) {
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    inbox.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                }
            });
            return inbox;
        }

        void send(String action, String json) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination("/app/stores/" + storeId + "/" + action);
            headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
            session.send(headers, json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        /** 구독 밖에서 온 프레임. ERROR 프레임이 여기로 온다. */
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            String message = headers.getFirst("message");
            errors.add(message != null ? message : new String((byte[]) payload, StandardCharsets.UTF_8));
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
                Throwable exception) {
            errors.add("exception: " + exception.getMessage());
        }
    }
}
