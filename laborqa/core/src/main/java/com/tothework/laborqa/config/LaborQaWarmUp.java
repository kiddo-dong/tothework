package com.tothework.laborqa.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 백그라운드에서 상담에 필요한 연결을 미리 맺는다.
 *
 * <p>벡터 저장소는 pgvector가 없는 환경에서도 앱이 뜨도록 처음 쓰일 때 만든다({@code @Lazy}). 그 대가로 재시작 뒤 첫
 * 질문이 연결 풀 생성·스키마 확인·OpenAI 첫 연결까지 모두 기다렸다. 앱이 다 뜬 뒤 별도 스레드에서 검색을 한 번
 * 해 두면 그 비용을 사용자가 아닌 서버가 낸다. 실패해도 경고만 남긴다 — 기동을 막지 않는다는 지연 생성의 이유를
 * 그대로 지킨다. 임베딩 한 번 값(1원 미만)이 든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LaborQaWarmUp {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final LaborQaProperties properties;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!properties.warmUpOnStartup()) {
            return;
        }
        long started = System.nanoTime();
        try {
            vectorStoreProvider.getObject().similaritySearch(SearchRequest.builder().query("주휴수당").topK(1).build());
            log.info("근로 상담 벡터 저장소 예열 완료 ({}ms)", (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn("근로 상담 벡터 저장소를 미리 연결하지 못했다. 첫 질문 때 다시 시도한다: {}", e.toString());
        }
    }
}
