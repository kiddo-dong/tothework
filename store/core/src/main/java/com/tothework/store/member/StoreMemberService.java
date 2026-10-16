package com.tothework.store.member;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소속만 다루는 멤버 기능. 고용 조건이 섞인 멤버 조회·수정은 bff가 workforce와 함께 조합한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreMemberService {

    private final StoreMemberRepository memberRepository;
    private final MembershipService membershipService;
    private final UserDirectory userDirectory;

    /** 멤버 요약 목록 (재직자만) — 매장 멤버 누구나 조회할 수 있다. */
    public List<StoreMemberSummaryResponse> getMemberSummaries(Long storeId, Long userId) {
        membershipService.requireMember(storeId, userId);
        List<StoreMember> active = memberRepository.findAllByStoreIdOrderByIdAsc(storeId).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .toList();
        Map<Long, UserSummary> users = userDirectory.findAll(active.stream().map(StoreMember::getUserId).toList());
        return active.stream()
                .map(member -> {
                    UserSummary user = users.get(member.getUserId());
                    return new StoreMemberSummaryResponse(member.getId(), user == null ? null : user.name(),
                            user == null ? null : user.profileImageUrl(), member.getRole(), member.getTitle());
                })
                .toList();
    }

    /** 본인 퇴사: 근무 이력 보존을 위해 행을 지우지 않고 INACTIVE로 바꾼다. */
    @Transactional
    public void leaveStore(Long storeId, Long userId) {
        StoreMember member = membershipService.requireActiveMember(storeId, userId);
        if (member.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 매장을 나갈 수 없습니다. 매장 삭제를 이용해 주세요.");
        }
        member.resign();
        memberRepository.save(member);
    }

    /** 관리자에 의한 퇴사 처리. */
    @Transactional
    public void removeMember(Long storeId, Long memberId, Long userId) {
        membershipService.requireOwnerOrManager(storeId, userId);
        StoreMember target = membershipService.memberOfStore(storeId, memberId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자는 퇴사 처리할 수 없습니다.");
        }
        target.resign();
        memberRepository.save(target);
    }
}
