package com.tothework.workforce.api;

import java.time.DayOfWeek;
import java.util.Set;

/** 멤버 수정 요청 중 workforce가 맡는 부분. null인 항목은 바꾸지 않는다. */
public record TermsChanges(Integer hourlyWage, TaxMode taxMode, Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay) {

    public boolean isEmpty() {
        return hourlyWage == null && taxMode == null && availableDays == null && weeklyHolidayDay == null;
    }
}
