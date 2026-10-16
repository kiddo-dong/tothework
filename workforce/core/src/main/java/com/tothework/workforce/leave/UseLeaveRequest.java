package com.tothework.workforce.leave;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UseLeaveRequest(@NotNull LocalDate leaveDate) {
}
