package com.tothework.laborqa.conversation;

import com.tothework.identity.api.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 사용자의 상담 이력을 지운다. 질문 내용은 민감할 수 있어 남기지 않는다.
 *
 * <p>identity의 탈퇴 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로 실행된다. 같은 트랜잭션을 전제하지 않으므로
 * laborqa를 다른 서비스로 떼어 내도 이 코드는 그대로다. 실패하면 발행 기록에 미완료로 남아 다시 전달된다.
 */
@Component
@RequiredArgsConstructor
class LaborQaWithdrawalListener {

    private final LaborQaSessionRepository sessionRepository;
    private final LaborQaMessageRepository messageRepository;

    @ApplicationModuleListener
    void on(UserWithdrawnEvent event) {
        // 메시지 -> 세션 순서 (같은 스키마 안의 외래키)
        messageRepository.deleteAllOfUser(event.userId());
        sessionRepository.deleteAllOfUser(event.userId());
    }
}
