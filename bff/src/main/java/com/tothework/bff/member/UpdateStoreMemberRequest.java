package com.tothework.bff.member;

import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.MembershipChanges;
import com.tothework.workforce.api.TaxMode;
import com.tothework.workforce.api.TermsChanges;
import java.time.DayOfWeek;
import java.util.Set;

/** 멤버 수정 요청. 기존과 같은 모양이며, 소속 부분은 store로, 고용 조건 부분은 workforce로 나뉜다. */
public record UpdateStoreMemberRequest(
        MemberRole role,
        /** 매장이 부르는 직함. 빈 문자열을 보내면 직함을 없앤다. 권한과는 무관하다. */
        String title,
        Integer hourlyWage,
        MemberStatus status,
        Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay,
        TaxMode taxMode) {

    MembershipChanges membershipPart() {
        return new MembershipChanges(role, title, status);
    }

    TermsChanges termsPart() {
        return new TermsChanges(hourlyWage, taxMode, availableDays, weeklyHolidayDay);
    }
}
