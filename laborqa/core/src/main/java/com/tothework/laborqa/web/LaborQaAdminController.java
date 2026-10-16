package com.tothework.laborqa.web;

import com.tothework.laborqa.config.LaborQaProperties;
import com.tothework.laborqa.knowledge.LaborQaIngestionService;
import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.ratelimit.RateLimit;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 근로기준법 Q&A 지식베이스 운영용 API.
 *
 * <p>지식베이스는 매장이 아니라 서비스 전체가 공유하는 자원이라 매장 역할(OWNER/MANAGER)로는 보호할 수
 * 없다. 전역 관리자 역할을 새로 만드는 대신 운영자만 아는 토큰을 요구한다. 토큰을 설정하지 않으면
 * 이 엔드포인트는 아예 열리지 않는다(운영 환경에서 실수로 무방비 노출되는 것을 막기 위함).
 */
@RestController
@RequestMapping("/api/v1/labor-qa/admin")
@RequiredArgsConstructor
public class LaborQaAdminController {

    private final LaborQaIngestionService ingestionService;

    private final LaborQaProperties properties;

    /**
     * 지식베이스를 벡터 저장소에 적재한다. 문서를 수정·추가한 뒤 호출한다.
     * 청크 ID가 내용 해시로 고정돼 있어 여러 번 호출해도 중복이 쌓이지 않는다.
     */
    @RateLimit(limit = 3, windowMinutes = 60)
    @PostMapping("/ingest")
    public ApiResponse<IngestResultResponse> ingest(@RequestHeader("X-Admin-Token") String token) {
        verifyAdminToken(token);
        return ApiResponse.success(new IngestResultResponse(ingestionService.ingest()));
    }

    private void verifyAdminToken(String token) {
        String adminToken = properties.adminIngestToken();
        if (adminToken == null || adminToken.isBlank()) {
            throw new ForbiddenException("관리자 토큰이 설정되지 않아 이 기능을 사용할 수 없습니다.");
        }
        // 타이밍 공격으로 토큰을 한 글자씩 알아내지 못하도록 길이와 무관하게 일정 시간이 걸리는 비교를 쓴다.
        boolean matches = MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), adminToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ForbiddenException("관리자 토큰이 올바르지 않습니다.");
        }
    }
}
