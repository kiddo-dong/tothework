package com.tothework.bff.member;

import com.tothework.shared.security.CurrentUserId;
import com.tothework.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 멤버 상세 조회·수정. 응답과 요청에 소속과 고용 조건이 섞여 있어 bff가 맡는다. 같은 경로 아래의 요약 조회·퇴사는
 * store가 직접 처리한다. 경로와 JSON은 기존과 같다.
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/members")
@RequiredArgsConstructor
class MemberController {

    private final MemberComposition memberComposition;

    @GetMapping
    ApiResponse<List<StoreMemberResponse>> getMembers(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(memberComposition.getMembers(storeId, userId));
    }

    @PatchMapping("/{memberId}")
    ApiResponse<StoreMemberResponse> updateMember(@PathVariable Long storeId, @PathVariable Long memberId,
            @CurrentUserId Long userId, @RequestBody UpdateStoreMemberRequest request) {
        return ApiResponse.success(memberComposition.updateMember(storeId, memberId, userId, request));
    }

    @PatchMapping("/me/available-days")
    ApiResponse<StoreMemberResponse> updateMyAvailableDays(@PathVariable Long storeId, @CurrentUserId Long userId,
            @Valid @RequestBody UpdateAvailableDaysRequest request) {
        return ApiResponse.success(memberComposition.updateMyAvailableDays(storeId, userId, request.availableDays()));
    }
}
