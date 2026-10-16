package com.tothework.store.store;

import com.tothework.store.api.MemberRole;

/** 내 매장 목록 항목: 매장 정보 + 이 매장에서의 내 역할. 프론트가 사장/알바 화면을 구분하는 데 쓴다. */
public record MyStoreResponse(StoreResponse store, Long myStoreMemberId, MemberRole myRole) {
}
