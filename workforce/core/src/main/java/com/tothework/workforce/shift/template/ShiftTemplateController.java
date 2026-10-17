package com.tothework.workforce.shift.template;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/shift-templates")
@RequiredArgsConstructor
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;

    /** 템플릿 등록 (OWNER/MANAGER). 예: "오픈" 09:00~15:00, 휴게 30분. */
    @PostMapping
    public ResponseEntity<ApiResponse<ShiftTemplateResponse>> createTemplate(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody ShiftTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(shiftTemplateService.createTemplate(storeId, userId, request)));
    }

    /** 템플릿 목록 — 멤버 누구나 (스케줄 화면에서 프리셋 버튼으로 사용). */
    /**
     * 근무 유형 일괄 등록 (OWNER/MANAGER).
     *
     * <p>배열 순서대로 순번이 매겨지며, 이름이 겹치거나 하나라도 실패하면 전부 등록되지 않는다.
     */
    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<ShiftTemplateResponse>>> createTemplates(
            @PathVariable Long storeId, @CurrentUserId Long userId,
            @Valid @RequestBody ShiftTemplateBulkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(shiftTemplateService.createTemplates(storeId, userId, request)));
    }

    @GetMapping
    public ApiResponse<List<ShiftTemplateResponse>> getTemplates(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(shiftTemplateService.getTemplates(storeId, userId));
    }

    @PatchMapping("/{templateId}")
    public ApiResponse<ShiftTemplateResponse> updateTemplate(@PathVariable Long storeId,
            @PathVariable Long templateId, @CurrentUserId Long userId,
            @Valid @RequestBody ShiftTemplateRequest request) {
        return ApiResponse.success(
                shiftTemplateService.updateTemplate(storeId, templateId, userId, request));
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long storeId, @PathVariable Long templateId,
            @CurrentUserId Long userId) {
        shiftTemplateService.deleteTemplate(storeId, templateId, userId);
        return ApiResponse.ok();
    }
}
