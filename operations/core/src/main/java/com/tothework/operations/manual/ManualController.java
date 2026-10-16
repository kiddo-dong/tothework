package com.tothework.operations.manual;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/manuals")
@RequiredArgsConstructor
public class ManualController {

    private final ManualService manualService;

    /** 매뉴얼 작성 (OWNER/MANAGER). */
    @PostMapping
    public ResponseEntity<ApiResponse<ManualResponse>> createManual(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody ManualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(manualService.createManual(storeId, userId, request)));
    }

    /** 매뉴얼 목록 (카테고리·순서 정렬, 본문 제외) — 멤버 누구나. */
    @GetMapping
    public ApiResponse<List<ManualSummaryResponse>> getManuals(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(manualService.getManuals(storeId, userId));
    }

    /** 매뉴얼 상세 (본문 + 이미지) — 멤버 누구나. */
    @GetMapping("/{manualId}")
    public ApiResponse<ManualResponse> getManual(@PathVariable Long storeId, @PathVariable Long manualId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(manualService.getManual(storeId, manualId, userId));
    }

    @PatchMapping("/{manualId}")
    public ApiResponse<ManualResponse> updateManual(@PathVariable Long storeId, @PathVariable Long manualId,
            @CurrentUserId Long userId, @Valid @RequestBody ManualRequest request) {
        return ApiResponse.success(manualService.updateManual(storeId, manualId, userId, request));
    }

    @DeleteMapping("/{manualId}")
    public ApiResponse<Void> deleteManual(@PathVariable Long storeId, @PathVariable Long manualId,
            @CurrentUserId Long userId) {
        manualService.deleteManual(storeId, manualId, userId);
        return ApiResponse.ok();
    }

    /** 매뉴얼용 이미지 업로드 → key와 표시용 url 반환. key를 저장 요청의 imageKeys에 담는다. */
    @PostMapping("/images")
    public ApiResponse<ManualImageResponse> uploadImage(@PathVariable Long storeId, @CurrentUserId Long userId,
            @RequestParam("image") MultipartFile image) {
        return ApiResponse.success(manualService.uploadImage(storeId, userId, image));
    }
}
