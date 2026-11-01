package com.tothework.laborqa.web;

import com.tothework.laborqa.knowledge.LaborQaIngestionService.IngestResult;

/**
 * 지식베이스 적재 결과.
 *
 * @param removedChunks 문서에서 사라졌거나 내용이 바뀌어 지운 옛 청크 수
 */
public record IngestResultResponse(int indexedChunks, int removedChunks) {

    static IngestResultResponse from(IngestResult result) {
        return new IngestResultResponse(result.indexedChunks(), result.removedChunks());
    }
}
