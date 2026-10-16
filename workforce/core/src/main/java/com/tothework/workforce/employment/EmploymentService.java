package com.tothework.workforce.employment;

import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.Employment;
import com.tothework.workforce.api.EmploymentTerms;
import com.tothework.workforce.api.TermsChanges;
import com.tothework.workforce.labor.LaborStandards;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고용 조건의 조회·변경. 누가 바꿀 수 있는지는 store에 묻고(권한·소속), 무엇으로 바꿀 수 있는지는 여기서 정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class EmploymentService implements Employment {

    private final MemberEmploymentRepository repository;
    private final StoreAccess storeAccess;
    private final Memberships memberships;

    @Override
    public EmploymentTerms termsOf(Long storeId, Long memberId) {
        return repository.findById(memberId)
                .map(MemberEmployment::toTerms)
                .orElseGet(() -> EmploymentTerms.defaultsFor(memberId, storeId));
    }

    @Override
    public Map<Long, EmploymentTerms> termsOfStore(Long storeId) {
        return repository.findAllByStoreId(storeId).stream()
                .map(MemberEmployment::toTerms)
                .collect(Collectors.toMap(EmploymentTerms::memberId, Function.identity()));
    }

    @Override
    public void validateChange(Long storeId, Long memberId, Long requesterUserId, TermsChanges changes) {
        check(storeId, memberId, requesterUserId, changes);
    }

    @Override
    @Transactional
    public EmploymentTerms applyChange(Long storeId, Long memberId, Long requesterUserId, TermsChanges changes) {
        check(storeId, memberId, requesterUserId, changes);
        MemberEmployment employment = loadOrStart(storeId, memberId);
        if (changes.hourlyWage() != null) {
            employment.changeHourlyWage(changes.hourlyWage());
        }
        if (changes.taxMode() != null) {
            employment.changeTaxMode(changes.taxMode());
        }
        if (changes.availableDays() != null) {
            employment.changeAvailableDays(changes.availableDays());
        }
        if (changes.weeklyHolidayDay() != null) {
            employment.changeWeeklyHolidayDay(changes.weeklyHolidayDay());
        }
        return repository.save(employment).toTerms();
    }

    @Override
    @Transactional
    public EmploymentTerms updateMyAvailableDays(Long storeId, Long userId, Set<DayOfWeek> availableDays) {
        Membership me = storeAccess.requireMember(storeId, userId);
        MemberEmployment employment = loadOrStart(storeId, me.memberId());
        employment.changeAvailableDays(availableDays);
        return repository.save(employment).toTerms();
    }

    /** 합류 이벤트보다 수정 요청이 먼저 오면 여기서 기본값으로 만든다. */
    MemberEmployment loadOrStart(Long storeId, Long memberId) {
        return repository.findById(memberId).orElseGet(() -> MemberEmployment.startFor(memberId, storeId));
    }

    /**
     * 고용 조건 변경 규칙.
     *
     * <p>관리자가 <b>자기 자신의</b> 시급·공제 방식을 고치는 것은 막는다. 매니저 자신의 행은 role이 MANAGER라 소유자
     * 검사를 통과하므로, 막지 않으면 요청 한 번으로 자기 시급을 올리거나 공제를 없앨 수 있다. 근무 가능 요일과 주휴일은
     * 금전·권한과 무관해 본인도 바꿀 수 있다.
     */
    private void check(Long storeId, Long memberId, Long requesterUserId, TermsChanges changes) {
        Membership requester = storeAccess.requireOwnerOrManager(storeId, requesterUserId);
        Membership target = memberships.getMemberOfStore(storeId, memberId);
        if (target.role() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자 정보는 이 API로 변경할 수 없습니다.");
        }
        boolean self = requester.memberId().equals(target.memberId());
        if (self && (changes.hourlyWage() != null || changes.taxMode() != null)) {
            throw new ForbiddenException("본인의 시급과 공제 방식은 변경할 수 없습니다. 매장 소유자에게 요청해 주세요.");
        }
        if (changes.hourlyWage() != null && changes.hourlyWage() < LaborStandards.MINIMUM_HOURLY_WAGE) {
            throw new InvalidRequestException(
                    "시급은 최저임금(" + LaborStandards.MINIMUM_HOURLY_WAGE + "원) 이상이어야 합니다.");
        }
    }
}
