package com.tothework.workforce.api;

import java.time.LocalDate;
import java.util.List;

/** 스케줄 조회. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface Schedules {

    /** 멤버의 기간 내 취소되지 않은 스케줄 (날짜·시작 시각 순). */
    List<ShiftResponse> activeShiftsOf(Long storeId, Long memberId, LocalDate from, LocalDate to);
}
