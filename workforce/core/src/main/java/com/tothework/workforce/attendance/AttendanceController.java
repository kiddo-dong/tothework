package com.tothework.workforce.attendance;

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
@RequestMapping("/api/v1/stores/{storeId}/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceReportService attendanceReportService;

    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceResponse>> createAttendance(@PathVariable Long storeId,
            @CurrentUserId Long userId, @Valid @RequestBody CreateAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(attendanceService.createAttendance(storeId, userId, request)));
    }

    @PostMapping("/clock-in")
    public ApiResponse<AttendanceResponse> clockIn(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(attendanceService.clockIn(storeId, userId));
    }

    @PostMapping("/clock-out")
    public ApiResponse<AttendanceResponse> clockOut(@PathVariable Long storeId, @CurrentUserId Long userId) {
        return ApiResponse.success(attendanceService.clockOut(storeId, userId));
    }

    @GetMapping("/me")
    public ApiResponse<List<AttendanceResponse>> getMyAttendance(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(attendanceService.getMyAttendance(storeId, userId, from, to));
    }

    @GetMapping
    public ApiResponse<List<AttendanceResponse>> getStoreAttendance(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(attendanceService.getStoreAttendance(storeId, userId, from, to));
    }

    @GetMapping("/report")
    public ApiResponse<List<AttendanceReportEntry>> getReport(@PathVariable Long storeId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long storeMemberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(attendanceReportService.getReport(storeId, userId, storeMemberId, from, to));
    }

    @PatchMapping("/{attendanceId}")
    public ApiResponse<AttendanceResponse> correctAttendance(@PathVariable Long storeId,
            @PathVariable Long attendanceId, @CurrentUserId Long userId,
            @Valid @RequestBody CorrectAttendanceRequest request) {
        return ApiResponse.success(attendanceService.correctAttendance(storeId, attendanceId, userId, request));
    }

    @DeleteMapping("/{attendanceId}")
    public ApiResponse<Void> deleteAttendance(@PathVariable Long storeId, @PathVariable Long attendanceId,
            @CurrentUserId Long userId) {
        attendanceService.deleteAttendance(storeId, attendanceId, userId);
        return ApiResponse.ok();
    }
}
