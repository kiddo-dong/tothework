package com.tothework.store.member;

import com.tothework.identity.api.WithdrawalGuard;
import com.tothework.shared.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 중인 매장이 있으면 탈퇴를 막는다. 근무 이력이 남은 채로 소속만 끊기는 일을 막기 위해서다.
 *
 * <p>삭제된 매장의 소속은 세지 않는다. 그 매장은 이미 나갈 방법이 없어(모든 API가 404) 탈퇴를 영영 막게 된다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ActiveMembershipWithdrawalGuard implements WithdrawalGuard {

    private final StoreMemberRepository memberRepository;

    @Override
    public void checkCanWithdraw(Long userId) {
        if (memberRepository.hasActiveMembership(userId)) {
            throw new ConflictException("소속된 매장이 있으면 탈퇴할 수 없습니다. 매장을 먼저 나가거나 삭제해 주세요.");
        }
    }
}
