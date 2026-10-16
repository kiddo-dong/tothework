package com.tothework.workforce.employment;

import com.tothework.identity.api.UserSummary;
import com.tothework.store.api.Membership;
import com.tothework.store.api.StorePolicy;
import com.tothework.workforce.api.EmploymentTerms;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 스케줄·근태 규칙을 판단하는 데 필요한 한 사람의 정보를 모은 것.
 *
 * <p>예전에는 {@code StoreMember} 엔티티 하나에서 매장 설정·사용자·고용 조건을 객체 그래프로 따라가 얻었다. 이제는
 * 각자 다른 서비스의 것이라 {@link Workers}가 한 번에 모아 넘긴다.
 */
public record Worker(Membership membership, EmploymentTerms terms, UserSummary user, StorePolicy policy) {

    public Long memberId() {
        return membership.memberId();
    }

    public Long storeId() {
        return membership.storeId();
    }

    /** 사용자 정보를 못 찾으면(탈퇴 직후 등) 이름 없이 진행한다. */
    public String name() {
        return user == null ? null : user.name();
    }

    public LocalDate birthDate() {
        return user == null ? null : user.birthDate();
    }

    public Set<DayOfWeek> availableDays() {
        return terms.availableDays();
    }

    public DayOfWeek weeklyHolidayDay() {
        return terms.weeklyHolidayDay();
    }
}
