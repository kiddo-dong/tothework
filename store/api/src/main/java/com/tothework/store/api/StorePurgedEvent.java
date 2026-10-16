package com.tothework.store.api;

/**
 * 삭제 유예기간이 지난 매장을 완전히 지웠다. 각 서비스는 이 매장의 데이터를 스스로 지운다.
 *
 * <p>예전에는 DB의 ON DELETE CASCADE가 모든 기능의 데이터를 한 번에 지웠다. 서비스가 나뉘면 다른 서비스의
 * 테이블에 CASCADE를 걸 수 없다.
 */
public record StorePurgedEvent(Long storeId) {
}
