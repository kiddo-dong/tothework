package com.tothework.store.api;

/** 매장 설정 조회. 삭제된 매장이면 404. */
public interface StorePolicies {

    StorePolicy policyOf(Long storeId);
}
