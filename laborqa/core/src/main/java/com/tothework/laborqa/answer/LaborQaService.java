package com.tothework.laborqa.answer;

import com.tothework.laborqa.config.LaborQaProperties;
import com.tothework.laborqa.conversation.LaborQaMessage;
import com.tothework.laborqa.conversation.LaborQaRole;
import com.tothework.laborqa.conversation.LaborQaSessionService;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 근로기준법·세무 상담을 RAG로 답한다. 검색한 근거 문서 안에서만 숫자를 말하도록 프롬프트로 제한한다.
 *
 * <p>단발 질문({@link #ask})과 세션 기반 멀티턴({@link #askInSession})을 지원한다. 프롬프트 문구와 검색 방식은
 * 기존 백엔드에서 그대로 옮겼다.
 */
@Service
@RequiredArgsConstructor
public class LaborQaService {

    /** 근거 문서를 못 찾았을 때 LLM에게 그 사실을 알리는 자리. 숫자를 지어내지 않게 하는 신호다. */
    private static final String NO_SOURCE_NOTICE =
            "(이번 질문에 맞는 자료를 찾지 못했어요. 제도 설명은 해도 되지만, 구체적인 숫자는 말하지 마세요.)";
    private static final String NOT_GROUNDED_ANSWER =
            "제가 가진 자료로는 답변드리기 어려워요. 정확한 내용은 노무사나 세무사에게 확인해 보시는 게 좋아요.";
    /**
     * 말투는 부드럽게 하되 이 문구의 힘은 빼지 않는다. 법률 자문이 아니라는 고지는 편하게 읽히더라도
     * 흘려 넘길 문장이 되면 곤란하다.
     */
    private static final String DISCLAIMER =
            "\n\n※ 이 답변은 법률·세무 자문이 아니에요. 중요한 결정을 앞두고 있다면 노무사·세무사에게 꼭 확인해 주세요.";

    private static final String SYSTEM_PROMPT = """
            당신은 한국에서 소규모 매장을 운영하거나 그곳에서 일하는 분들을 위한
            근로기준법·세무 안내 도우미예요.

            [말투]
            부드럽고 친근한 해요체로 써요. "~합니다"보다 "~해요", "~이에요"를 쓰고,
            법 조문을 그대로 옮긴 듯한 딱딱한 문장은 쉬운 말로 풀어서 설명해요.
            묻는 사람이 대부분 법을 잘 모르는 알바생과 사장님이라, 어려운 용어를 쓸 때는 짧게 뜻을 덧붙여요.

            [다룰 수 있는 범위]
            한국의 근로기준법과 그에 딸린 세무·4대보험 이야기만 다뤄요.
            그 밖의 질문(날씨, 요리, 일반 상식 등)에는 답하지 말고
            "근로·급여와 관련된 질문에만 답할 수 있어요"라고 안내해요.

            [아는 지식과 근거 문서의 경계 — 가장 중요해요]
            제도가 어떻게 돌아가는지에 대한 일반적인 설명은, 근거 문서에 없더라도
            알고 있는 범위에서 해도 돼요. 주휴수당이 무엇인지, 연차가 어떻게 쌓이는지 같은 것들이에요.

            하지만 구체적인 숫자는 달라요. 최저임금 액수, 4대보험 요율, 연차 일수,
            가산수당 배율, 연도별 기준처럼 값이 정해져 있는 것은
            아래 근거 문서에 있는 것만 말해요.
            문서에 없는 숫자는 기억에 의존해 말하지 말고, 제도만 설명한 뒤
            "정확한 최신 기준은 확인이 필요해요"라고 알려줘요.
            법은 해마다 바뀌고, 틀린 숫자로 급여를 계산하면 그것 자체가 법 위반이 돼요.
            모른다고 말하는 것보다 옛날 숫자를 자신 있게 말하는 쪽이 훨씬 위험해요.

            [그 외]
            이전 대화가 함께 제공되면 질문의 맥락(예: "그럼 5인 미만은요?")을 해석하는 데 사용해요.
            개인의 정확한 세액이나 구체적인 법적 판단은 하지 말고 일반적인 기준만 안내해요.
            근거 문서를 참고했다면 답변 마지막 줄에 그 출처(문서 제목)를 나열해요.
            """;

    /** 벡터 저장소는 처음 쓰일 때 접속하도록 지연 조회한다 (LaborQaAiConfig 참고). */
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    /** 서비스마다 자기 ChatClient를 갖는다 (workforce에는 스케줄 초안용이 따로 있다). */
    @Qualifier("laborQaChatClient")
    private final ChatClient chatClient;
    private final LaborQaSessionService sessionService;
    private final LaborQaProperties properties;

    /** 단발 질문. 대화 이력을 저장하지도 참조하지도 않는다. */
    public LaborQaAnswer ask(String question) {
        return answer(question, question, "");
    }

    /**
     * 세션 기반 멀티턴 질문. 직전 대화를 프롬프트에 넣어 "그럼 ~는요?" 같은 후속 질문을 이해시키고, 질문·답변을
     * 저장한다. LLM 응답을 기다리는 동안 DB 커넥션을 붙잡지 않도록 이 메서드 자체는 트랜잭션이 아니다.
     */
    public LaborQaAnswer askInSession(Long sessionId, Long userId, String question) {
        sessionService.requireOwnedSession(sessionId, userId);
        List<LaborQaMessage> history = sessionService.loadRecentMessages(sessionId);

        // 후속 질문은 대명사("그럼", "그거") 때문에 단독으로는 검색이 빗나가기 쉬워, 직전 사용자 질문을
        // 검색 쿼리에 함께 넣어 재현율을 높인다 (LLM 재작성 호출 없이 해결하는 저비용 방식).
        String retrievalQuery = lastUserQuestion(history)
                .map(previous -> previous + " " + question)
                .orElse(question);

        LaborQaAnswer response = answer(retrievalQuery, question, formatHistory(history));
        sessionService.recordExchange(sessionId, userId, question, response.answer(), response.sources());
        return response;
    }

    private LaborQaAnswer answer(String retrievalQuery, String question, String historyBlock) {
        VectorStore vectorStore = vectorStoreProvider.getObject();
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(retrievalQuery)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .build());
        // 검색이 비었다고 여기서 끊지 않는다. 자료가 9개 파일뿐이라 제도만 물어도 걸리지 않는 질문이
        // 많은데, 그때마다 "자료가 없어요"만 답하면 아는 것도 못 알려주는 셈이 된다. 대신 근거가
        // 없다는 사실을 함께 넘겨서, 설명은 하되 숫자는 지어내지 않도록 한다.
        String context = results.isEmpty() ? NO_SOURCE_NOTICE : results.stream()
                .map(document -> "[" + resolveSourceLabel(document) + "]\n" + document.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text("{history}근거 문서:\n{context}\n\n질문: {question}")
                        .param("history", historyBlock)
                        .param("context", context)
                        .param("question", question))
                .call()
                .content();
        if (answer == null || answer.isBlank()) {
            return new LaborQaAnswer(NOT_GROUNDED_ANSWER, List.of(), false);
        }

        List<String> sources = results.stream()
                .map(this::resolveSourceLabel)
                .distinct()
                .toList();
        // grounded는 "우리 자료로 뒷받침됐는지"를 뜻한다. 아는 지식만으로 답한 경우는 false다.
        return new LaborQaAnswer(answer + DISCLAIMER, sources, !results.isEmpty());
    }

    private Optional<String> lastUserQuestion(List<LaborQaMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == LaborQaRole.USER) {
                return Optional.of(history.get(i).getContent());
            }
        }
        return Optional.empty();
    }

    private String formatHistory(List<LaborQaMessage> history) {
        if (history.isEmpty()) {
            return "";
        }
        String lines = history.stream()
                .map(message -> (message.getRole() == LaborQaRole.USER ? "사용자: " : "도우미: ")
                        + message.getContent())
                .collect(Collectors.joining("\n"));
        return "이전 대화:\n" + lines + "\n\n";
    }

    /** 마크다운/JSON은 title 메타데이터를, PDF는 파일명+페이지 번호를 출처 표시로 쓴다. */
    private String resolveSourceLabel(Document document) {
        var metadata = document.getMetadata();
        if (metadata.containsKey("title")) {
            return String.valueOf(metadata.get("title"));
        }
        if (metadata.containsKey("file_name")) {
            Object pageNumber = metadata.get("page_number");
            return pageNumber != null
                    ? metadata.get("file_name") + " (p." + pageNumber + ")"
                    : String.valueOf(metadata.get("file_name"));
        }
        return "출처 미상";
    }
}
