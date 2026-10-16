package com.tothework.laborqa.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * {@code laborqa/core/src/main/resources/labor-docs/} 아래 PDF·JSON 문서를 읽어 벡터 저장소(PostgreSQL/pgvector)에 적재한다.
 *
 * <p>기동할 때마다 자동 적재하지 않는다. 저장소가 영속이라 재기동마다 넣으면 같은 내용이 쌓이고,
 * 청크가 늘어날수록 기동이 임베딩 API 호출에 묶여 느려지고 실패 지점도 늘기 때문이다. 대신 지식베이스를
 * 갱신했을 때 관리자가 명시적으로 호출한다.
 *
 * <p>청크 ID는 내용 해시로 고정한다. 같은 문서를 다시 적재하면 같은 키를 덮어쓰므로 중복이 생기지 않는다.
 * 다만 문서를 삭제한 뒤 재적재해도 예전 청크는 남으므로, 그럴 때는 벡터 인덱스를 비우고 다시 넣어야 한다.
 *
 * <p>JSON 파일은 {@code [{"title": "...", "content": "..."}, ...]} 형태의 배열이어야 하며, title은
 * 답변 출처 표시에 쓰인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaborQaIngestionService {

    private static final String PDF_LOCATION_PATTERN = "classpath:labor-docs/pdf/*.pdf";
    private static final String JSON_LOCATION_PATTERN = "classpath:labor-docs/json/*.json";

    /** 벡터 저장소는 처음 쓰일 때 접속하도록 지연 조회한다 (LaborQaAiConfig 참고). */
    private final ObjectProvider<VectorStore> vectorStoreProvider;

    /**
     * 지식베이스를 벡터 저장소에 적재하고 적재한 청크 수를 돌려준다.
     * 실패는 호출자에게 그대로 전파해 관리자가 결과를 알 수 있게 한다.
     */
    public int ingest() {
        List<Document> rawDocuments = new ArrayList<>();
        rawDocuments.addAll(readPdfs());
        rawDocuments.addAll(readJson());

        // 폐기 예정인 기본 생성자를 일부러 유지한다. 청크 id가 내용 해시라, 나누는 기준이 조금만 달라져도 이미
        // 적재된 벡터와 다른 청크가 생겨 중복으로 쌓인다. 바꾸려면 벡터 인덱스를 비우고 다시 적재해야 한다.
        @SuppressWarnings("removal")
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.split(rawDocuments).stream()
                .map(LaborQaIngestionService::withStableId)
                .toList();
        vectorStoreProvider.getObject().add(chunks);

        log.info("근로기준법 Q&A 지식베이스 적재 완료: 문서 {}개 -> 청크 {}개", rawDocuments.size(), chunks.size());
        return chunks.size();
    }

    /**
     * 내용이 같으면 항상 같은 ID가 나오도록 내용에서 ID를 유도한다 (재적재 시 덮어쓰기 → 중복 방지).
     *
     * <p>pgvector의 id 컬럼이 uuid 타입이라 임의의 해시 문자열은 넣을 수 없다. 그래서 해시를 그대로
     * 쓰지 않고 내용으로부터 UUID를 만든다 — 결정적이면서 형식도 만족한다.
     */
    private static Document withStableId(Document chunk) {
        String id = UUID.nameUUIDFromBytes(chunk.getText().getBytes(StandardCharsets.UTF_8)).toString();
        return new Document(id, chunk.getText(), chunk.getMetadata());
    }

    private List<Document> readPdfs() {
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resolveResources(PDF_LOCATION_PATTERN)) {
            documents.addAll(new PagePdfDocumentReader(resource).get());
        }
        return documents;
    }

    private List<Document> readJson() {
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resolveResources(JSON_LOCATION_PATTERN)) {
            documents.addAll(new JsonReader(resource,
                    item -> Map.of("title", item.getOrDefault("title", "출처 미상")), "content").get());
        }
        return documents;
    }

    private List<Resource> resolveResources(String locationPattern) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            return List.of(resources);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
