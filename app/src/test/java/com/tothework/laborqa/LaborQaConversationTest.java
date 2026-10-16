package com.tothework.laborqa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.tothework.identity.api.UserWithdrawnEvent;
import com.tothework.laborqa.conversation.LaborQaMessage;
import com.tothework.laborqa.conversation.LaborQaRole;
import com.tothework.laborqa.conversation.LaborQaSession;
import com.tothework.laborqa.conversation.LaborQaSessionService;
import com.tothework.shared.error.NotFoundException;
import com.tothework.support.TestDatabase;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상담 대화가 실제 DB(tw_laborqa)에 저장·조회·삭제되는지, 그리고 탈퇴 이벤트를 비동기로 받아 이력을 지우는지 확인한다.
 *
 * <p>Spring Data JDBC로 옮기면서 변경 감지가 없어졌으므로, 제목처럼 나중에 바뀌는 값이 명시적으로 저장되는지도 본다.
 */
@SpringBootTest
@Import(TestDatabase.class)
class LaborQaConversationTest {

    @Autowired private LaborQaSessionService sessionService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;

    private static long newUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE);
    }

    @Test
    void anExchangeIsStoredAndTheTitleComesFromTheFirstQuestion() {
        long userId = newUserId();
        LaborQaSession session = sessionService.createSession(userId);

        sessionService.recordExchange(session.getId(), userId, "주휴수당은 몇 시간부터 받을 수 있나요? 아르바이트도 되나요?",
                "주 15시간 이상이면 받을 수 있어요.", List.of("03-weekly-holiday", "faq"));

        LaborQaSession reloaded = sessionService.requireOwnedSession(session.getId(), userId);
        assertThat(reloaded.getTitle()).isEqualTo("주휴수당은 몇 시간부터 받을 수 있나요? 아르바이트도 ");
        assertThat(reloaded.getCreatedAt()).isNotNull();

        List<LaborQaMessage> messages = sessionService.getMessages(session.getId(), userId);
        assertThat(messages).extracting(LaborQaMessage::getRole).containsExactly(LaborQaRole.USER, LaborQaRole.ASSISTANT);
        assertThat(messages.get(1).getSourceList()).containsExactly("03-weekly-holiday", "faq");
    }

    /** 남의 세션은 있다는 사실도 드러내지 않는다. */
    @Test
    void someoneElsesSessionLooksLikeItDoesNotExist() {
        LaborQaSession session = sessionService.createSession(newUserId());

        assertThatThrownBy(() -> sessionService.requireOwnedSession(session.getId(), newUserId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingASessionRemovesItsMessages() {
        long userId = newUserId();
        LaborQaSession session = sessionService.createSession(userId);
        sessionService.recordExchange(session.getId(), userId, "연차는 언제 생기나요?", "1년에 80% 이상 출근하면 생겨요.", List.of());

        sessionService.deleteSession(session.getId(), userId);

        assertThat(sessionService.getSessionsOf(userId)).isEmpty();
    }

    /**
     * identity가 탈퇴를 커밋하면 laborqa가 별도 트랜잭션에서 비동기로 이력을 지운다. 이벤트는 트랜잭션 안에서
     * 발행돼야 커밋 후에 전달되므로, 실제 탈퇴 흐름처럼 트랜잭션 안에서 발행한다.
     */
    @Test
    void withdrawalEventEventuallyRemovesTheUsersHistory() {
        long userId = newUserId();
        long otherUserId = newUserId();
        LaborQaSession session = sessionService.createSession(userId);
        sessionService.recordExchange(session.getId(), userId, "4대보험 요율이 궁금해요", "자료를 확인해 볼게요.", List.of());
        sessionService.createSession(otherUserId);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new UserWithdrawnEvent(userId)));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(sessionService.getSessionsOf(userId)).isEmpty());
        assertThat(sessionService.getSessionsOf(otherUserId)).hasSize(1);
    }
}
