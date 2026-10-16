package com.tothework.operations.notice;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
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

@RestController
@RequestMapping("/api/v1/stores/{storeId}/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /** 공지 작성 (OWNER/MANAGER). */
    @PostMapping
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody CreateNoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(noticeService.createNotice(storeId, userId, request)));
    }

    /** 공지 목록 (내 확인 여부 + 확인한 인원수 포함) — 멤버 누구나. */
    @GetMapping
    public ApiResponse<List<NoticeResponse>> getNotices(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(noticeService.getNotices(storeId, userId));
    }

    /** "확인했습니다" 버튼 — 멤버 누구나, 멱등. */
    @PostMapping("/{noticeId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long storeId, @PathVariable Long noticeId,
            @CurrentUserId Long userId) {
        noticeService.markRead(storeId, noticeId, userId);
        return ApiResponse.ok();
    }

    /** 멤버별 확인 현황 (안 읽은 사람 확인용) — OWNER/MANAGER. */
    @GetMapping("/{noticeId}/reads")
    public ApiResponse<List<NoticeReadStatusResponse>> getReadStatus(@PathVariable Long storeId,
            @PathVariable Long noticeId, @CurrentUserId Long userId) {
        return ApiResponse.success(noticeService.getReadStatus(storeId, noticeId, userId));
    }

    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> deleteNotice(@PathVariable Long storeId, @PathVariable Long noticeId,
            @CurrentUserId Long userId) {
        noticeService.deleteNotice(storeId, noticeId, userId);
        return ApiResponse.ok();
    }
}
