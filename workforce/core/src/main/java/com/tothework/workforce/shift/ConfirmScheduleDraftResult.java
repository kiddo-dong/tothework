package com.tothework.workforce.shift;

import com.tothework.workforce.api.ShiftResponse;
import java.util.List;

public record ConfirmScheduleDraftResult(
        List<ShiftResponse> created,
        List<RejectedScheduleDraftItem> rejected
) {
}
