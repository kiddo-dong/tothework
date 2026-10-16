package com.tothework.workforce.shift;

import java.util.List;

public record ConfirmScheduleDraftResult(
        List<ShiftResponse> created,
        List<RejectedScheduleDraftItem> rejected
) {
}
