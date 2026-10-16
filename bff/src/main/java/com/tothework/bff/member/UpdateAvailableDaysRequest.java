package com.tothework.bff.member;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.util.Set;

public record UpdateAvailableDaysRequest(@NotNull Set<DayOfWeek> availableDays) {
}
