package com.tothework.store.member;

import com.tothework.shared.security.CurrentUserId;
import com.tothework.shared.web.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소속만 다루는 멤버 API. 같은 경로 아래의 멤버 상세 조회·수정·근무 가능 요일 변경은 응답에 고용 조건이 섞여 bff가
 * 맡는다 (MemberController).
 */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/members")
@RequiredArgsConstructor
public class StoreMemberController {

    private final StoreMemberService storeMemberService;

    @GetMapping("/summary")
    public ApiResponse<List<StoreMemberSummaryResponse>> getMemberSummaries(@PathVariable Long storeId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(storeMemberService.getMemberSummaries(storeId, userId));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> leaveStore(@PathVariable Long storeId, @CurrentUserId Long userId) {
        storeMemberService.leaveStore(storeId, userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long storeId, @PathVariable Long memberId,
            @CurrentUserId Long userId) {
        storeMemberService.removeMember(storeId, memberId, userId);
        return ApiResponse.ok();
    }
}
