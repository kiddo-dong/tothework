package com.tothework.store.api;

import java.time.LocalDateTime;

/**
 * 매장 소속 한 건. 다른 서비스는 store의 엔티티 대신 이 값을 받는다.
 *
 * <p>고용 조건(시급·공제·주휴일·근무 가능 요일)은 여기 없다. workforce가 같은 {@code memberId}로 따로 갖는다.
 *
 * @param joinedAt 입사(재입사) 시각. 연차 산정의 기준이다
 */
public record Membership(Long memberId, Long storeId, Long userId, MemberRole role, MemberStatus status,
        String title, LocalDateTime joinedAt, LocalDateTime resignedAt) {

    public boolean isOwnerOrManager() {
        return role == MemberRole.OWNER || role == MemberRole.MANAGER;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }
}
