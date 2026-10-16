package com.tothework.store.member;

import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import com.tothework.store.api.Membership;
import com.tothework.store.api.MembershipChanges;
import com.tothework.store.api.Memberships;
import com.tothework.store.api.StoreAccess;
import com.tothework.store.store.StoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** store가 다른 서비스에 내주는 소속·권한 계약의 구현. 엔티티는 밖으로 나가지 않고 {@link Membership}만 나간다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MembershipService implements StoreAccess, Memberships {

    private final StoreMemberRepository memberRepository;
    private final StoreRepository storeRepository;

    // ---------- 권한 ----------

    @Override
    public Membership requireMember(Long storeId, Long userId) {
        return requireActiveMember(storeId, userId).toMembership();
    }

    @Override
    public Membership requireOwnerOrManager(Long storeId, Long userId) {
        StoreMember member = requireActiveMember(storeId, userId);
        if (!member.isOwnerOrManager()) {
            throw new ForbiddenException("매장 관리자만 수행할 수 있는 작업입니다.");
        }
        return member.toMembership();
    }

    @Override
    public Membership requireOwner(Long storeId, Long userId) {
        StoreMember member = requireActiveMember(storeId, userId);
        if (member.getRole() != MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자만 수행할 수 있는 작업입니다.");
        }
        return member.toMembership();
    }

    StoreMember requireActiveMember(Long storeId, Long userId) {
        StoreMember member = memberRepository.findInActiveStore(storeId, userId).orElseThrow(() -> notAMemberOf(storeId));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ForbiddenException("퇴사 처리된 멤버는 매장 기능을 이용할 수 없습니다.");
        }
        return member;
    }

    /**
     * 못 찾은 이유를 나눠 안내한다. 삭제된 매장도 멤버가 없는 것으로 나오는데, 방금 지운 매장을 열어 둔 탭에서 다시
     * 눌렀을 때 "멤버가 아니다"라고 하면 무슨 일인지 알 수 없다. 이미 실패한 경로에서만 한 번 더 조회한다.
     */
    private RuntimeException notAMemberOf(Long storeId) {
        if (!storeRepository.existsActive(storeId)) {
            return new NotFoundException("삭제되었거나 존재하지 않는 매장입니다.");
        }
        return new ForbiddenException("해당 매장의 멤버가 아닙니다.");
    }

    // ---------- 조회 ----------

    @Override
    public Membership getMemberOfStore(Long storeId, Long memberId) {
        return memberOfStore(storeId, memberId).toMembership();
    }

    @Override
    public List<Membership> findAllByStore(Long storeId) {
        return memberRepository.findAllByStoreIdOrderByIdAsc(storeId).stream().map(StoreMember::toMembership).toList();
    }

    StoreMember memberOfStore(Long storeId, Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> member.getStoreId().equals(storeId))
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다."));
    }

    // ---------- 수정 (bff가 workforce 쪽과 나눠 맡긴다) ----------

    @Override
    public void validateChange(Long storeId, Long memberId, Long requesterUserId, MembershipChanges changes) {
        check(storeId, memberId, requesterUserId, changes);
    }

    @Override
    @Transactional
    public Membership applyChange(Long storeId, Long memberId, Long requesterUserId, MembershipChanges changes) {
        StoreMember target = check(storeId, memberId, requesterUserId, changes);
        if (changes.role() != null) {
            target.changeRole(changes.role());
        }
        // 직함은 지울 수 있어야 하므로 빈 문자열도 받는다. null은 "안 보냄"이라 건드리지 않는다.
        if (changes.title() != null) {
            target.changeTitle(changes.title());
        }
        if (changes.status() != null) {
            target.changeStatus(changes.status());
        }
        return memberRepository.save(target).toMembership();
    }

    /**
     * 역할·직함·재직 상태 변경 규칙. 시급·공제 같은 고용 조건 규칙은 workforce가 같은 방식으로 따로 검사한다.
     *
     * <p>관리자가 <b>자기 자신의</b> 역할·재직 상태를 바꾸는 것은 막는다. 관리자 권한은 다른 사람을 관리하라고 준 것이지
     * 자기 대우를 정하라고 준 것이 아니다. 직함은 금전·권한과 무관해 본인도 바꿀 수 있다.
     */
    private StoreMember check(Long storeId, Long memberId, Long requesterUserId, MembershipChanges changes) {
        StoreMember requester = requireActiveMember(storeId, requesterUserId);
        if (!requester.isOwnerOrManager()) {
            throw new ForbiddenException("매장 관리자만 수행할 수 있는 작업입니다.");
        }
        StoreMember target = memberOfStore(storeId, memberId);
        if (target.getRole() == MemberRole.OWNER) {
            throw new ForbiddenException("매장 소유자 정보는 이 API로 변경할 수 없습니다.");
        }
        boolean self = requester.getId().equals(target.getId());
        if (self && changes.role() != null) {
            throw new ForbiddenException("본인의 역할은 변경할 수 없습니다.");
        }
        if (self && changes.status() != null) {
            throw new ForbiddenException("본인의 재직 상태는 이 API로 변경할 수 없습니다. 매장 나가기를 이용해 주세요.");
        }
        if (changes.role() == MemberRole.OWNER) {
            throw new ForbiddenException("소유권 이전은 이 API로 할 수 없습니다. 소유권 이전 기능을 이용해 주세요.");
        }
        if (changes.title() != null && changes.title().strip().length() > StoreMember.TITLE_MAX_LENGTH) {
            throw new InvalidRequestException("직함은 " + StoreMember.TITLE_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return target;
    }
}
