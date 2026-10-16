package com.tothework.workforce.payroll;

import com.tothework.shared.web.ApiResponse;
import com.tothework.shared.security.CurrentUserId;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final StoreDashboardService storeDashboardService;

    /** 월간 대시보드: 인건비 합계, 멤버별 비용, 근태 준수 현황 (OWNER/MANAGER). */
    @GetMapping("/api/v1/stores/{storeId}/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(@PathVariable Long storeId,
            @CurrentUserId Long userId, @RequestParam int year, @RequestParam int month) {
        return ApiResponse.success(storeDashboardService.getDashboard(storeId, userId, year, month));
    }

    /** 일일 대시보드: 그날의 근무·근태 요약, 기본 오늘 (OWNER/MANAGER). */
    @GetMapping("/api/v1/stores/{storeId}/dashboard/daily")
    public ApiResponse<DailyDashboardResponse> getDailyDashboard(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(storeDashboardService.getDailyDashboard(storeId, userId,
                date == null ? LocalDate.now() : date));
    }

    /** 주간 대시보드: 주 52시간·주휴 15시간 모니터링, date가 속한 주(월~일), 기본 이번 주 (OWNER/MANAGER). */
    @GetMapping("/api/v1/stores/{storeId}/dashboard/weekly")
    public ApiResponse<WeeklyDashboardResponse> getWeeklyDashboard(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(storeDashboardService.getWeeklyDashboard(storeId, userId,
                date == null ? LocalDate.now() : date));
    }
}
