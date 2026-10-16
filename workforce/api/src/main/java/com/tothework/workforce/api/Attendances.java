package com.tothework.workforce.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 근태 조회. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface Attendances {

    /** 지금 근무 중이면 출근 시각. */
    Optional<LocalDateTime> workingSince(Long memberId);

    /** 그날 매장의 취소되지 않은 스케줄마다 근무자와 근태 판정 (시작 시각 순). */
    List<RosterEntry> rosterOn(Long storeId, LocalDate date);
}
