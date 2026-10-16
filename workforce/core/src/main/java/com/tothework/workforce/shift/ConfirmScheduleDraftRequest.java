package com.tothework.workforce.shift;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmScheduleDraftRequest(
        @NotEmpty @Valid List<ScheduleDraftItem> items
) {
}
