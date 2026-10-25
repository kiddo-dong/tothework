package com.tothework.shared.web;

import com.tothework.shared.error.InvalidRequestException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 조회 API가 받는 기간({@code from}~{@code to})의 검증.
 *
 * <p>근태·스케줄·인수인계 조회는 결과를 한 번에 메모리로 읽고 리포트는 그 위에서 계산한다. 기간에 제한이 없으면 몇 년치
 * 요청 하나가 작은 서버의 메모리를 차지한다. 화면이 쓰는 기간(하루~한 달, 길어야 분기)을 넉넉히 덮는 1년으로 막는다.
 */
public final class DateRanges {

    public static final int MAX_DAYS = 366;

    private DateRanges() {
    }

    public static void requireValid(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("조회 종료일은 시작일 이후여야 합니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidRequestException("한 번에 조회할 수 있는 기간은 최대 " + MAX_DAYS + "일입니다.");
        }
    }
}
