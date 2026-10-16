package com.tothework.store.api;

import java.util.List;

/** 다른 서비스가 매장 소속을 조회하고, bff가 멤버 수정 중 소속 부분을 맡기는 입구. */
public interface Memberships {

    /** 매장에 속한 멤버. 없거나 다른 매장 소속이면 같은 404로 응답해 다른 매장의 존재를 드러내지 않는다. */
    Membership getMemberOfStore(Long storeId, Long memberId);

    /** 퇴사자를 포함한 매장의 모든 멤버. */
    List<Membership> findAllByStore(Long storeId);

    /**
     * 소속 변경이 허용되는지만 확인한다. 아무것도 바꾸지 않는다. 멤버 수정은 store와 workforce에 걸쳐 있어,
     * 양쪽 검증을 모두 통과한 뒤에 적용해야 한쪽만 바뀌는 일이 없다.
     */
    void validateChange(Long storeId, Long memberId, Long requesterUserId, MembershipChanges changes);

    /** {@link #validateChange}를 다시 확인한 뒤 적용한다. */
    Membership applyChange(Long storeId, Long memberId, Long requesterUserId, MembershipChanges changes);
}
