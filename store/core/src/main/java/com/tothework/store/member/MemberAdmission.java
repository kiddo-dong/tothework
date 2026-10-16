package com.tothework.store.member;

import com.tothework.shared.error.ConflictException;
import com.tothework.store.api.MemberJoinedEvent;
import com.tothework.store.api.MemberRole;
import com.tothework.store.api.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매장 합류. 매장 개설자와 가입 승인 모두 여기를 거친다.
 *
 * <p>합류하면 {@link MemberJoinedEvent}를 낸다. 고용 조건(시급 등)은 store가 정하지 않고, 이 이벤트를 받은
 * workforce가 기본값으로 만든다.
 */
@Service
@RequiredArgsConstructor
public class MemberAdmission {

    private final StoreMemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 퇴사했던 사람이 돌아오면 근무 이력이 이어지도록 새 행을 만들지 않고 기존 행을 되살린다. */
    @Transactional
    public StoreMember admit(Long storeId, Long userId, MemberRole role) {
        StoreMember existing = memberRepository.findByStoreIdAndUserId(storeId, userId).orElse(null);
        if (existing != null && existing.getStatus() == MemberStatus.ACTIVE) {
            throw new ConflictException("이미 해당 매장의 멤버입니다.");
        }
        boolean rejoined = existing != null;
        StoreMember member;
        if (rejoined) {
            existing.rejoin(role);
            member = memberRepository.save(existing);
        } else {
            member = memberRepository.save(StoreMember.join(storeId, userId, role));
        }
        eventPublisher.publishEvent(new MemberJoinedEvent(member.getId(), storeId, userId, role, rejoined));
        return member;
    }
}
