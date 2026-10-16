package com.tothework.bff.home;

import com.tothework.shared.security.CurrentUserId;
import com.tothework.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeComposition homeComposition;

    /** 매장 홈 화면. 역할에 따라 공통(myDay) / 관리자 섹션이 채워진다. */
    @GetMapping("/api/v1/stores/{storeId}/home")
    public ApiResponse<HomeResponse> getHome(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(homeComposition.getHome(storeId, userId));
    }
}
