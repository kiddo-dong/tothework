package com.tothework.store.api;

/**
 * 매장 단위 권한 확인. 모든 서비스가 매장 데이터를 다루기 전에 가장 먼저 부른다.
 *
 * <p>삭제된 매장은 여기서 404가 나므로, 이 확인을 거친 뒤의 데이터 접근은 삭제된 매장에 닿지 않는다. 예전에는
 * 각 테이블이 stores와 JOIN해 삭제 여부를 걸렀는데, 서비스가 나뉘면 그 JOIN은 불가능하다.
 */
public interface StoreAccess {

    /** 재직 중인 멤버. 아니면 403, 매장이 없거나 삭제됐으면 404. */
    Membership requireMember(Long storeId, Long userId);

    Membership requireOwnerOrManager(Long storeId, Long userId);

    Membership requireOwner(Long storeId, Long userId);
}
