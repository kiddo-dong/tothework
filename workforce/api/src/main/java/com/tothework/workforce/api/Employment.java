package com.tothework.workforce.api;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Set;

/** 고용 조건 조회와, bff가 멤버 수정 중 고용 조건 부분을 맡기는 입구. */
public interface Employment {

    /** 아직 만들어지지 않았으면(합류 이벤트 처리 전) 기본값을 돌려준다. */
    EmploymentTerms termsOf(Long storeId, Long memberId);

    /** 매장 멤버들의 고용 조건. memberId → 조건. 없는 멤버는 빠진다. */
    Map<Long, EmploymentTerms> termsOfStore(Long storeId);

    /** 변경이 허용되는지만 확인한다 (최저임금, 관리자 본인의 시급·공제 변경 금지 등). 아무것도 바꾸지 않는다. */
    void validateChange(Long storeId, Long memberId, Long requesterUserId, TermsChanges changes);

    /** {@link #validateChange}를 다시 확인한 뒤 적용한다. */
    EmploymentTerms applyChange(Long storeId, Long memberId, Long requesterUserId, TermsChanges changes);

    /** 본인의 근무 가능 요일. 금전·권한과 무관해 직원 본인이 바꿀 수 있다. */
    EmploymentTerms updateMyAvailableDays(Long storeId, Long userId, Set<DayOfWeek> availableDays);
}
