package com.tothework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 서버 OS가 UTC여도 "오늘"과 출퇴근 시각이 한국 시간으로 정해지는지. */
class ServiceTimeZoneTest {

    private TimeZone original;

    @BeforeEach
    void rememberAndSimulateAUtcServer() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void startupPinsTheJvmToKoreanTime() {
        TotheworkApplication.useServiceTimeZone();

        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    /** UTC 서버에서 한국 시각 오전 8시 반에 출근하면, 근무일이 전날로 찍히지 않아야 한다. */
    @Test
    void aMorningClockInIsDatedToday() {
        TotheworkApplication.useServiceTimeZone();
        ZonedDateTime morningInSeoul = ZonedDateTime.of(2026, 3, 2, 8, 30, 0, 0, ZoneId.of("Asia/Seoul"));

        LocalDateTime recorded = LocalDateTime.ofInstant(morningInSeoul.toInstant(), ZoneId.systemDefault());

        assertThat(recorded).isEqualTo(LocalDateTime.of(2026, 3, 2, 8, 30));
    }
}
