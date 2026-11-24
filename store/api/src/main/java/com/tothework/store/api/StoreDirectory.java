package com.tothework.store.api;

import java.util.List;

/** 매장 목록 조회. 다른 서비스가 매장마다 갖춰야 할 데이터를 한꺼번에 맞출 때 쓴다(예: 기존 매장의 채팅방 채우기). */
public interface StoreDirectory {

    /** 삭제되지 않은 모든 매장의 id. */
    List<Long> findAllActiveIds();
}
