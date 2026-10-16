package com.tothework.laborqa.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Spring Data JDBC에는 변경 감지가 없다. 객체를 바꾸고 저장을 빠뜨리면 조용히 반영되지 않으므로, 제목이 바뀔 때만
 * 세션을 다시 저장하는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class LaborQaSessionServiceTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 3L;

    @Mock private LaborQaSessionRepository sessionRepository;
    @Mock private LaborQaMessageRepository messageRepository;
    @InjectMocks private LaborQaSessionService sessionService;

    @Test
    void theFirstQuestionSetsTheTitleAndSavesTheSession() {
        LaborQaSession untitled = new LaborQaSession(SESSION_ID, USER_ID, null, null, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(untitled));

        sessionService.recordExchange(SESSION_ID, USER_ID, "최저임금이 얼마예요?", "답변", List.of());

        assertThat(untitled.getTitle()).isEqualTo("최저임금이 얼마예요?");
        verify(sessionRepository).save(untitled);
    }

    @Test
    void laterQuestionsKeepTheTitleWithoutRewritingTheSession() {
        LaborQaSession titled = new LaborQaSession(SESSION_ID, USER_ID, "처음 질문", null, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(titled));

        sessionService.recordExchange(SESSION_ID, USER_ID, "그럼 5인 미만은요?", "답변", List.of());

        assertThat(titled.getTitle()).isEqualTo("처음 질문");
        verify(sessionRepository, never()).save(any());
    }
}
