package com.tothework.operations.handover;

import com.tothework.operations.api.HandoverNoteResponse;
import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/handover-notes")
@RequiredArgsConstructor
public class HandoverNoteController {

    private final HandoverNoteService handoverNoteService;

    /** 인수인계 작성 — 멤버 누구나. */
    @PostMapping
    public ResponseEntity<ApiResponse<HandoverNoteResponse>> createNote(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody CreateHandoverNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(handoverNoteService.createNote(storeId, userId, request)));
    }

    /** 기간별 조회 (최신순) — 멤버 누구나. */
    @GetMapping
    public ApiResponse<List<HandoverNoteResponse>> getNotes(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(handoverNoteService.getNotes(storeId, userId, from, to));
    }

    /** 수정 — 작성자 본인 또는 관리자. workDate를 생략하면 원래 날짜를 유지한다. */
    @PatchMapping("/{noteId}")
    public ApiResponse<HandoverNoteResponse> updateNote(@PathVariable Long storeId,
            @PathVariable Long noteId, @CurrentUserId Long userId,
            @Valid @RequestBody CreateHandoverNoteRequest request) {
        return ApiResponse.success(handoverNoteService.updateNote(storeId, noteId, userId, request));
    }

    /** 삭제 — 작성자 본인 또는 관리자. */
    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> deleteNote(@PathVariable Long storeId, @PathVariable Long noteId,
            @CurrentUserId Long userId) {
        handoverNoteService.deleteNote(storeId, noteId, userId);
        return ApiResponse.ok();
    }
}
