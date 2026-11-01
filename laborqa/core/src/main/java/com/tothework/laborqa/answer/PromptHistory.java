package com.tothework.laborqa.answer;

import com.tothework.laborqa.conversation.LaborQaMessage;
import com.tothework.laborqa.conversation.LaborQaRole;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 프롬프트에 넣을 이전 대화.
 *
 * <p>이전 대화는 "그럼 5인 미만은요?" 같은 후속 질문의 맥락을 알려 주려고 넣는다. 그 용도에는 최근 몇 번의 문답과 각
 * 답변의 앞부분이면 충분하다. 저장된 답변을 통째로 넣으면 대화가 길어질수록 입력 토큰이 늘어 첫 글자가 늦게 나오고
 * 비용도 커진다. 특히 답변마다 붙는 면책 문구는 맥락에 아무 도움이 안 되는데 매번 함께 들어갔다.
 */
final class PromptHistory {

    /** 메시지 하나를 이 길이에서 자른다. 후속 질문을 해석하는 데는 답변의 요지면 된다. */
    static final int MAX_MESSAGE_LENGTH = 300;

    private PromptHistory() {
    }

    /** 오래된 것부터 받은 메시지를 "이전 대화" 블록으로 만든다. 비어 있으면 빈 문자열. */
    static String format(List<LaborQaMessage> history) {
        if (history.isEmpty()) {
            return "";
        }
        String lines = history.stream()
                .map(message -> (message.getRole() == LaborQaRole.USER ? "사용자: " : "도우미: ")
                        + shorten(withoutDisclaimer(message.getContent())))
                .collect(Collectors.joining("\n"));
        return "이전 대화:\n" + lines + "\n\n";
    }

    private static String withoutDisclaimer(String content) {
        return content.endsWith(LaborQaService.DISCLAIMER)
                ? content.substring(0, content.length() - LaborQaService.DISCLAIMER.length())
                : content;
    }

    private static String shorten(String content) {
        String trimmed = content.strip();
        return trimmed.length() <= MAX_MESSAGE_LENGTH ? trimmed : trimmed.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
