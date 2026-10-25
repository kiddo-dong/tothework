package com.tothework.shared.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tothework.shared.error.InvalidRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangesTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void aSingleDayAndAFullYearAreAllowed() {
        assertThatCode(() -> DateRanges.requireValid(START, START)).doesNotThrowAnyException();
        assertThatCode(() -> DateRanges.requireValid(START, START.plusDays(DateRanges.MAX_DAYS - 1)))
                .doesNotThrowAnyException();
    }

    /** 몇 년치를 한 번에 읽어 메모리에서 계산하는 요청을 막는다. */
    @Test
    void aLongerRangeIsRejected() {
        assertThatThrownBy(() -> DateRanges.requireValid(START, START.plusDays(DateRanges.MAX_DAYS)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("최대");
    }

    @Test
    void aReversedRangeIsRejected() {
        assertThatThrownBy(() -> DateRanges.requireValid(START, START.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("이후");
    }
}
