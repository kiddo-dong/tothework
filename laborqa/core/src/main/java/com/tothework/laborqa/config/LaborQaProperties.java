package com.tothework.laborqa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 근로 상담 설정.
 *
 * @param similarityThreshold 이 점수를 넘는 문서만 근거로 쓴다. 실제 질문을 받아 보며 맞추는 값이라 설정으로 뺐다
 * @param topK                근거로 가져올 최대 문서 수
 * @param adminIngestToken    지식베이스 적재 API용 토큰. 비어 있으면 그 API는 열리지 않는다
 * @param vectorStore         근거 문서를 담는 pgvector 접속 정보. 업무 DB(MySQL)와 별개다
 */
@ConfigurationProperties("tothework.laborqa")
public record LaborQaProperties(
        double similarityThreshold,
        int topK,
        String adminIngestToken,
        VectorStore vectorStore) {

    public record VectorStore(String url, String username, String password) {
    }
}
