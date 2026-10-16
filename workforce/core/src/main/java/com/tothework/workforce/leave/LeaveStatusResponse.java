package com.tothework.workforce.leave;

import java.util.List;

public record LeaveStatusResponse(
        int entitledDays,
        int usedDays,
        int remainingDays,
        List<LeaveUsageResponse> usages
) {
}
