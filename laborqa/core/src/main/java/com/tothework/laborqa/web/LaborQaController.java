package com.tothework.laborqa.web;

import com.tothework.laborqa.answer.LaborQaAnswer;
import com.tothework.laborqa.answer.LaborQaService;
import com.tothework.laborqa.conversation.LaborQaSession;
import com.tothework.laborqa.conversation.LaborQaSessionService;
import com.tothework.shared.ratelimit.RateLimit;
import com.tothework.shared.security.CurrentUserId;
import com.tothework.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 근로기준법·세무 상담. 매장과 무관하게 로그인한 사용자라면 누구나 쓸 수 있다. 경로와 응답은 기존과 같다. */
@RestController
@RequestMapping("/api/v1/labor-qa")
@RequiredArgsConstructor
public class LaborQaController {

    private final LaborQaService laborQaService;
    private final LaborQaSessionService sessionService;

    /** 단발 질문 (대화 이력 없음). */
    @RateLimit(limit = 10, windowMinutes = 1)
    @PostMapping("/ask")
    public ApiResponse<LaborQaAnswer> ask(@Valid @RequestBody LaborQaRequest request) {
        return ApiResponse.success(laborQaService.ask(request.question()));
    }

    /** 대화 세션 생성. 이후 이 세션 안에서 후속 질문이 맥락을 유지한다. */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<LaborQaSessionResponse>> createSession(@CurrentUserId Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(LaborQaSessionResponse.from(sessionService.createSession(userId))));
    }

    /** 내 대화 목록 (최신순). */
    @GetMapping("/sessions")
    public ApiResponse<List<LaborQaSessionResponse>> getMySessions(@CurrentUserId Long userId) {
        return ApiResponse.success(sessionService.getSessionsOf(userId).stream()
                .map(LaborQaSessionResponse::from)
                .toList());
    }

    /** 대화 상세 (전체 메시지 이력). 본인 세션만 조회할 수 있다. */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<LaborQaSessionDetailResponse> getSession(@PathVariable Long sessionId,
            @CurrentUserId Long userId) {
        LaborQaSession session = sessionService.requireOwnedSession(sessionId, userId);
        return ApiResponse.success(LaborQaSessionDetailResponse.from(session,
                sessionService.getMessages(sessionId, userId)));
    }

    /** 세션 안에서 질문. 직전 대화가 프롬프트에 포함되고, 질문·답변이 이력으로 저장된다. */
    @RateLimit(limit = 10, windowMinutes = 1)
    @PostMapping("/sessions/{sessionId}/ask")
    public ApiResponse<LaborQaAnswer> askInSession(@PathVariable Long sessionId,
            @CurrentUserId Long userId, @Valid @RequestBody LaborQaRequest request) {
        return ApiResponse.success(laborQaService.askInSession(sessionId, userId, request.question()));
    }

    /** 대화 삭제 (메시지 포함). */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId, @CurrentUserId Long userId) {
        sessionService.deleteSession(sessionId, userId);
        return ApiResponse.ok();
    }
}
