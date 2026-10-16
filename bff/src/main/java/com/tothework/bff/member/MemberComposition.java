package com.tothework.bff.member;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import com.tothework.workforce.api.Employment;
import com.tothework.workforce.api.EmploymentTerms;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 소속(store)과 고용 조건(workforce)에 걸친 멤버 API를 조합한다. 규칙은 두지 않는다 — 누가 무엇을 바꿀 수 있는지는
 * 데이터를 가진 각 서비스가 검사하고, 여기서는 나누고 합치기만 한다.
 *
 * <p><b>트랜잭션으로 묶지 않는다.</b> 지금은 같은 DB라 묶을 수 있지만, 묶으면 서비스를 나누는 순간 깨지는 전제를
 * 숨기게 된다. 대신 양쪽 검증을 모두 통과시킨 뒤에 적용해, 적용 단계에서 실패할 이유를 없앤다.
 */
@Service
@RequiredArgsConstructor
class MemberComposition {

    private final StoreAccess storeAccess;
    private final Memberships memberships;
    private final Employment employment;
    private final UserDirectory userDirectory;

    /** 멤버 전체 상세 목록 (시급·이메일·공제 방식 등 민감 정보 포함) — OWNER/MANAGER. */
    List<StoreMemberResponse> getMembers(Long storeId, Long userId) {
        storeAccess.requireOwnerOrManager(storeId, userId);
        List<Membership> members = memberships.findAllByStore(storeId);
        Map<Long, EmploymentTerms> terms = employment.termsOfStore(storeId);
        Map<Long, UserSummary> users = userDirectory.findAll(members.stream().map(Membership::userId).toList());
        return members.stream()
                .map(member -> StoreMemberResponse.of(member,
                        terms.getOrDefault(member.memberId(), EmploymentTerms.defaultsFor(member.memberId(), storeId)),
                        users.get(member.userId())))
                .toList();
    }

    StoreMemberResponse updateMember(Long storeId, Long memberId, Long userId, UpdateStoreMemberRequest request) {
        // ① 양쪽 검증. 고용 조건 쪽을 먼저 보는 이유는 기존 오류 메시지의 우선순위(시급·공제 → 역할 → 상태)를 지키기 위해서다.
        employment.validateChange(storeId, memberId, userId, request.termsPart());
        memberships.validateChange(storeId, memberId, userId, request.membershipPart());

        // ② 적용
        Membership membership = request.membershipPart().isEmpty()
                ? memberships.getMemberOfStore(storeId, memberId)
                : memberships.applyChange(storeId, memberId, userId, request.membershipPart());
        EmploymentTerms terms = request.termsPart().isEmpty()
                ? employment.termsOf(storeId, memberId)
                : employment.applyChange(storeId, memberId, userId, request.termsPart());
        return compose(membership, terms);
    }

    StoreMemberResponse updateMyAvailableDays(Long storeId, Long userId, Set<DayOfWeek> availableDays) {
        EmploymentTerms terms = employment.updateMyAvailableDays(storeId, userId, availableDays);
        return compose(memberships.getMemberOfStore(storeId, terms.memberId()), terms);
    }

    private StoreMemberResponse compose(Membership membership, EmploymentTerms terms) {
        UserSummary user = userDirectory.find(membership.userId()).orElse(null);
        return StoreMemberResponse.of(membership, terms, user);
    }
}
