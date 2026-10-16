package com.tothework.workforce.shift;

import java.time.LocalDate;

public record SkippedShiftDate(LocalDate date, String reason) {
}
