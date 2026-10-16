package com.tothework.workforce.labor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tothework.shared.error.InvalidRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LaborStandardsTest {

    @Test
    void isMinor_returnsTrueUnderEighteen() {
        LocalDate birthDate = LocalDate.of(2010, 1, 1);
        LocalDate onDate = LocalDate.of(2027, 12, 31); // 17세 364일

        assertThat(LaborStandards.isMinor(birthDate, onDate)).isTrue();
    }

    @Test
    void isMinor_returnsFalseOnEighteenthBirthday() {
        LocalDate birthDate = LocalDate.of(2010, 1, 1);
        LocalDate onDate = LocalDate.of(2028, 1, 1); // 정확히 18세

        assertThat(LaborStandards.isMinor(birthDate, onDate)).isFalse();
    }

    @Test
    void isMinor_returnsFalseWhenBirthDateMissing() {
        assertThat(LaborStandards.isMinor(null, LocalDate.of(2026, 1, 1))).isFalse();
    }

    @Test
    void statutoryBreakMinutes_underFourHours_requiresNone() {
        assertThat(LaborStandards.statutoryBreakMinutes(3 * 60)).isZero();
    }

    @Test
    void statutoryBreakMinutes_fourToEightHours_requiresThirtyMinutes() {
        assertThat(LaborStandards.statutoryBreakMinutes(4 * 60)).isEqualTo(30);
        assertThat(LaborStandards.statutoryBreakMinutes(7 * 60 + 59)).isEqualTo(30);
    }

    @Test
    void statutoryBreakMinutes_eightHoursOrMore_requiresOneHour() {
        assertThat(LaborStandards.statutoryBreakMinutes(8 * 60)).isEqualTo(60);
    }

    @Test
    void resolveBreakMinutes_statutoryAndUnrequested_appliesMinimumAutomatically() {
        // 9시간 체류 - 법정 강제 정책, 요청값 없음 -> 자동으로 최소 휴게 적용
        assertThat(LaborStandards.resolveBreakMinutes(true, 9 * 60, null)).isEqualTo(60);
    }

    @Test
    void resolveBreakMinutes_freePolicyAndUnrequested_defaultsToZero() {
        assertThat(LaborStandards.resolveBreakMinutes(false, 9 * 60, null)).isZero();
    }

    @Test
    void resolveBreakMinutes_requestedBelowStatutoryMinimum_throws() {
        // 9시간 체류면 법정 최소 60분인데 20분만 요청 -> 거부
        assertThatThrownBy(() -> LaborStandards.resolveBreakMinutes(true, 9 * 60, 20))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void resolveBreakMinutes_requestedNotShorterThanSpan_throws() {
        assertThatThrownBy(() -> LaborStandards.resolveBreakMinutes(false, 8 * 60, 8 * 60))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void mondayOfWeek_andSundayOfWeek_bracketTheIsoWeek() {
        LocalDate wednesday = LocalDate.of(2026, 1, 7);

        assertThat(LaborStandards.mondayOfWeek(wednesday)).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(LaborStandards.sundayOfWeek(wednesday)).isEqualTo(LocalDate.of(2026, 1, 11));
    }
}
