package com.tothework.operations.handover;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** workDate를 생략하면 오늘 날짜로 기록된다. */
public record CreateHandoverNoteRequest(
        @NotBlank String content,
        LocalDate workDate
) {
}
