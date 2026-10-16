package com.tothework.workforce.shift;

import com.tothework.workforce.api.ShiftResponse;
import java.util.List;

public record RecurringShiftResult(List<ShiftResponse> created, List<SkippedShiftDate> skipped) {
}
