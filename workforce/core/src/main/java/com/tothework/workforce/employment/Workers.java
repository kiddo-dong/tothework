package com.tothework.workforce.employment;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.store.api.Membership;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StorePolicies;
import com.tothework.store.api.StorePolicy;
import com.tothework.workforce.api.EmploymentTerms;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 규칙 판단에 필요한 {@link Worker}를 만든다. 소속·매장 설정은 store에, 이름·생년월일은 identity에 묻고, 고용 조건은
 * workforce의 것을 쓴다. 목록은 서비스마다 한 번씩만 묻도록 일괄로 조회한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Workers {

    private final Memberships memberships;
    private final StorePolicies storePolicies;
    private final UserDirectory userDirectory;
    private final MemberEmploymentRepository employmentRepository;
    private final EmploymentTermsReader termsReader;

    /** 스케줄을 배정할 수 있는 사람. 이 매장의 재직 중인 멤버여야 한다. */
    public Worker assignable(Long storeId, Long memberId) {
        Membership membership = requireActive(memberships.getMemberOfStore(storeId, memberId));
        return worker(membership, termsOf(storeId, memberId));
    }

    /**
     * {@link #assignable}과 같고, 고용 조건 행에 쓰기 락을 건다. 겹침·주 52시간·연소자 주 35시간 검사는 모두 "기존
     * 스케줄을 모아 보고 괜찮으면 새로 넣는" 방식이라, 두 요청이 동시에 오면 둘 다 통과해 합치면 상한을 넘는 스케줄이
     * 저장된다. 그래서 멤버 한 명 단위로 쓰기를 한 줄로 세운다.
     *
     * <p>예전에는 store의 소속 행을 잠갔다. 다른 서비스의 행을 잠글 수는 없으므로 workforce가 가진 고용 조건 행을
     * 잠근다. 이 락을 쓰는 트랜잭션은 반드시 READ COMMITTED여야 앞 요청이 방금 커밋한 스케줄이 보인다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Worker lockAssignable(Long storeId, Long memberId) {
        Membership membership = requireActive(memberships.getMemberOfStore(storeId, memberId));
        return worker(membership, lock(storeId, memberId));
    }

    /** 재직 여부와 상관없이 잠근다. 퇴사한 멤버의 스케줄도 취소는 할 수 있어야 한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Worker lockAny(Long storeId, Long memberId) {
        return worker(memberships.getMemberOfStore(storeId, memberId), lock(storeId, memberId));
    }

    /** 재직자 전원. AI 스케줄 초안처럼 매장 전체를 놓고 판단할 때 쓴다. */
    public List<Worker> activeWorkersOf(Long storeId) {
        List<Membership> active = memberships.findAllByStore(storeId).stream().filter(Membership::isActive).toList();
        StorePolicy policy = storePolicies.policyOf(storeId);
        Map<Long, UserSummary> users = userDirectory.findAll(active.stream().map(Membership::userId).toList());
        Map<Long, EmploymentTerms> terms = termsReader.ofStore(storeId);
        return active.stream()
                .map(member -> new Worker(member,
                        terms.getOrDefault(member.memberId(), EmploymentTerms.defaultsFor(member.memberId(), storeId)),
                        users.get(member.userId()), policy))
                .toList();
    }

    /** 응답에 이름·사진을 채우기 위한 매장 멤버들의 사용자 정보. memberId → 사용자. 한 번에 조회한다. */
    public Map<Long, UserSummary> profilesOf(Long storeId, Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Membership> members = memberships.findAllByStore(storeId).stream()
                .filter(member -> memberIds.contains(member.memberId()))
                .toList();
        Map<Long, UserSummary> users = userDirectory.findAll(members.stream().map(Membership::userId).toList());
        return members.stream()
                .filter(member -> users.containsKey(member.userId()))
                .collect(Collectors.toMap(Membership::memberId, member -> users.get(member.userId())));
    }

    private Worker worker(Membership membership, EmploymentTerms terms) {
        UserSummary user = userDirectory.find(membership.userId()).orElse(null);
        return new Worker(membership, terms, user, storePolicies.policyOf(membership.storeId()));
    }

    private EmploymentTerms termsOf(Long storeId, Long memberId) {
        return employmentRepository.findById(memberId).map(MemberEmployment::toTerms)
                .orElseGet(() -> EmploymentTerms.defaultsFor(memberId, storeId));
    }

    /**
     * 고용 조건 행을 잠근다. 행이 아직 없으면(합류 이벤트 처리 전) 기본값으로 만들고 다시 잠근다. 두 요청이 동시에
     * 만들려 해도 늦은 쪽은 값을 바꾸지 않고 같은 행의 락을 기다린다. (중복 키 예외로 처리하면 트랜잭션이 롤백
     * 전용이 된다.) 거의 모든 요청은 행이 이미 있으므로 잠그기부터 한다.
     */
    private EmploymentTerms lock(Long storeId, Long memberId) {
        return employmentRepository.findLockedByMemberId(memberId)
                .or(() -> {
                    employmentRepository.insertDefaultIfAbsent(memberId, storeId);
                    return employmentRepository.findLockedByMemberId(memberId);
                })
                .orElseThrow()
                .toTerms();
    }

    private static Membership requireActive(Membership membership) {
        if (!membership.isActive()) {
            throw new InvalidRequestException("퇴사 처리된 멤버에게는 스케줄을 배정할 수 없습니다.");
        }
        return membership;
    }
}
