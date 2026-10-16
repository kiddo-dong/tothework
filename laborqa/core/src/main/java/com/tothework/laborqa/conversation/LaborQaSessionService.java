package com.tothework.laborqa.conversation;

import com.tothework.shared.error.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대화 세션과 메시지의 저장을 맡는다. LLM 호출은 하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LaborQaSessionService {

    private final LaborQaSessionRepository sessionRepository;
    private final LaborQaMessageRepository messageRepository;

    /** userId는 인증된 요청에서 온 값이라 identity에 다시 묻지 않는다. */
    @Transactional
    public LaborQaSession createSession(Long userId) {
        return sessionRepository.save(LaborQaSession.startFor(userId));
    }

    public List<LaborQaSession> getSessionsOf(Long userId) {
        return sessionRepository.findAllByUserIdOrderByIdDesc(userId);
    }

    public List<LaborQaMessage> getMessages(Long sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        return messageRepository.findAllBySessionIdOrderByIdAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        LaborQaSession session = requireOwnedSession(sessionId, userId);
        messageRepository.deleteAllOfSession(session.getId());
        sessionRepository.delete(session);
    }

    /** 소유자 검증. 남의 세션은 존재 여부도 드러내지 않도록 같은 404로 응답한다. */
    public LaborQaSession requireOwnedSession(Long sessionId, Long userId) {
        LaborQaSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("대화를 찾을 수 없습니다."));
        if (!session.isOwnedBy(userId)) {
            throw new NotFoundException("대화를 찾을 수 없습니다.");
        }
        return session;
    }

    /**
     * 프롬프트용 최근 대화, 오래된 것부터. 토큰 비용이 무한히 늘지 않도록 최근 10건(질문+답변 합산)만 쓴다.
     */
    public List<LaborQaMessage> loadRecentMessages(Long sessionId) {
        return messageRepository.findTop10BySessionIdOrderByIdDesc(sessionId).reversed();
    }

    /**
     * 질문·답변 한 쌍을 저장한다. LLM 호출이 끝난 뒤에만 불리므로, 응답을 기다리는 수 초 동안 DB 커넥션을
     * 트랜잭션으로 붙잡아 두지 않는다.
     *
     * <p>제목 변경은 변경 감지가 없으므로 직접 저장한다.
     */
    @Transactional
    public void recordExchange(Long sessionId, Long userId, String question, String answer, List<String> sources) {
        LaborQaSession session = requireOwnedSession(sessionId, userId);
        if (session.getTitle() == null) {
            session.fillTitleIfEmpty(question);
            sessionRepository.save(session);
        }
        messageRepository.save(LaborQaMessage.question(sessionId, question));
        messageRepository.save(LaborQaMessage.answer(sessionId, answer, sources));
    }
}
