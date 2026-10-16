package com.tothework.store.invite;

import com.tothework.identity.api.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴한 사용자의 가입 신청을 지운다. identity의 탈퇴가 커밋된 뒤 별도 트랜잭션에서 비동기로 실행된다.
 * 소속(store_members)은 근무 이력이 참조하므로 지우지 않는다 — 탈퇴는 활동 중인 매장이 없을 때만 가능하다.
 */
@Component
@RequiredArgsConstructor
class JoinRequestWithdrawalListener {

    private final JoinRequestRepository joinRequestRepository;

    @ApplicationModuleListener
    void on(UserWithdrawnEvent event) {
        joinRequestRepository.deleteAllOfUser(event.userId());
    }
}
