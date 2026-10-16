package com.tothework.workforce.api;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * 멤버 한 명의 고용 조건. store의 소속({@code Membership})과 같은 {@code memberId}로 연결된다.
 *
 * <p>예전에는 store의 멤버 엔티티에 함께 있었지만, 읽는 곳이 급여와 스케줄뿐이라 workforce가 갖는다.
 *
 * @param weeklyHolidayDay 주휴일. 정하지 않았으면 null
 */
public record EmploymentTerms(Long memberId, Long storeId, int hourlyWage, TaxMode taxMode,
        Set<DayOfWeek> availableDays, DayOfWeek weeklyHolidayDay) {

    /** 합류 직후 아직 정하지 않은 조건. 관리자가 시급을 정하기 전에는 0원이다. */
    public static EmploymentTerms defaultsFor(Long memberId, Long storeId) {
        return new EmploymentTerms(memberId, storeId, 0, TaxMode.NONE, Set.of(), null);
    }
}
