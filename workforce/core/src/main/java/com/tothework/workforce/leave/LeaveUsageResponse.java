package com.tothework.workforce.leave;

import java.time.LocalDate;

public record LeaveUsageResponse(Long id, LocalDate leaveDate) {

    static LeaveUsageResponse from(LeaveUsage leaveUsage) {
        return new LeaveUsageResponse(leaveUsage.getId(), leaveUsage.getLeaveDate());
    }
}
