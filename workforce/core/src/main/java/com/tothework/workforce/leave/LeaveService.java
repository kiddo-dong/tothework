package com.tothework.workforce.leave;

import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차(유급휴가) 발생·사용 관리.
 *
 * <p>발생 기준(근로기준법 제60조를 단순화): 입사 1년 미만은 1개월당 1일(최대 11일), 1년 이상은 매년 15일을 추가
 * 부여한다. 개근·80% 출근율 요건과 미사용 연차의 소멸, 3년차부터의 가산은 아직 반영하지 않는다. 입사일은 store의
 * 소속 정보에서 온다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private static final int FIRST_YEAR_MAX_DAYS = 11;
    private static final int ANNUAL_DAYS = 15;

    private final LeaveUsageRepository leaveUsageRepository;
    private final Memberships memberships;
    private final StoreAccess storeAccess;

    public LeaveStatusResponse getLeaveStatus(Long storeId, Long memberId, Long userId) {
        Membership requester = storeAccess.requireMember(storeId, userId);
        Membership target = memberships.getMemberOfStore(storeId, memberId);
        if (!requester.memberId().equals(target.memberId()) && !requester.isOwnerOrManager()) {
            throw new ForbiddenException("본인 또는 매장 관리자만 조회할 수 있습니다.");
        }
        int entitled = entitledDays(target.joinedAt().toLocalDate(), LocalDate.now());
        List<LeaveUsageResponse> usages = leaveUsageRepository.findAllByMemberIdOrderByLeaveDateDesc(target.memberId())
                .stream()
                .map(LeaveUsageResponse::from)
                .toList();
        int used = usages.size();
        return new LeaveStatusResponse(entitled, used, entitled - used, usages);
    }

    @Transactional
    public LeaveUsageResponse useLeave(Long storeId, Long memberId, Long userId, UseLeaveRequest request) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        Membership target = memberships.getMemberOfStore(storeId, memberId);
        if (!target.isActive()) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 연차를 사용할 수 없습니다.");
        }
        if (leaveUsageRepository.existsByMemberIdAndLeaveDate(target.memberId(), request.leaveDate())) {
            throw new ConflictException("해당 날짜에 이미 연차가 사용되었습니다.");
        }
        int entitled = entitledDays(target.joinedAt().toLocalDate(), LocalDate.now());
        long used = leaveUsageRepository.countByMemberId(target.memberId());
        if (used >= entitled) {
            throw new InvalidRequestException("남은 연차가 없습니다. (발생 " + entitled + "일, 사용 " + used + "일)");
        }
        return LeaveUsageResponse.from(
                leaveUsageRepository.save(LeaveUsage.on(target.memberId(), storeId, request.leaveDate())));
    }

    @Transactional
    public void cancelLeave(Long storeId, Long leaveId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        LeaveUsage leaveUsage = leaveUsageRepository.findByIdAndStoreId(leaveId, storeId)
                .orElseThrow(() -> new NotFoundException("연차 사용 기록을 찾을 수 없습니다."));
        leaveUsageRepository.delete(leaveUsage);
    }

    static int entitledDays(LocalDate joinedDate, LocalDate today) {
        long months = ChronoUnit.MONTHS.between(joinedDate, today);
        if (months < 12) {
            return (int) Math.min(months, FIRST_YEAR_MAX_DAYS);
        }
        long years = months / 12;
        return (int) (FIRST_YEAR_MAX_DAYS + ANNUAL_DAYS * years);
    }
}
