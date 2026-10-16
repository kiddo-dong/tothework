package com.tothework.bff.member;

import com.tothework.identity.api.UserSummary;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TaxMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 멤버 상세. 기존 백엔드와 같은 JSON이지만 값은 세 서비스에서 온다: 소속(store), 고용 조건(workforce), 이름·사진(identity).
 */
public record StoreMemberResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        MemberRole role,
        /** 매장이 부르는 직함. 안 정했으면 null이며, 그때는 역할 이름을 보여 주면 된다. */
        String title,
        int hourlyWage,
        MemberStatus status,
        LocalDateTime joinedAt,
        LocalDateTime resignedAt,
        Set<DayOfWeek> availableDays,
        DayOfWeek weeklyHolidayDay,
        TaxMode taxMode) {

    static StoreMemberResponse of(Membership membership, EmploymentTerms terms, UserSummary user) {
        return new StoreMemberResponse(membership.memberId(), membership.userId(),
                user == null ? null : user.name(), user == null ? null : user.email(),
                user == null ? null : user.profileImageUrl(), membership.role(), membership.title(),
                terms.hourlyWage(), membership.status(), membership.joinedAt(), membership.resignedAt(),
                terms.availableDays(), terms.weeklyHolidayDay(), terms.taxMode());
    }
}
