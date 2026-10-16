package com.tothework.workforce.shift;

import java.util.List;

public record RecurringShiftResult(List<ShiftResponse> created, List<SkippedShiftDate> skipped) {
}
