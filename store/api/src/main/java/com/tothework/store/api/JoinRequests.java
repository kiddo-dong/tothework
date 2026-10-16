package com.tothework.store.api;

/** 가입 신청 현황. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface JoinRequests {

    /** 매장에 들어와 처리를 기다리는 신청 수. */
    long countPending(Long storeId);
}
