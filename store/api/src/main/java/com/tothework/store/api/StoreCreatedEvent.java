package com.tothework.store.api;

/**
 * 매장이 새로 생겼다. 매장 생성 트랜잭션 안에서 발행된다.
 *
 * <p>매장과 함께 있어야 하는 것을 만드는 서비스가 받는다(예: chat의 매장 채팅방). 매장이 만들어진 순간 함께 있어야 하는
 * 것은 {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}로 받아 같은 트랜잭션에서 만든다. 그러면 둘 중
 * 하나만 남는 일이 없다.
 */
public record StoreCreatedEvent(Long storeId) {
}
