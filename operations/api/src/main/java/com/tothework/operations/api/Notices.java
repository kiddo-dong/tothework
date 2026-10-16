package com.tothework.operations.api;

/** 공지 현황. 권한 확인은 부르는 쪽이 먼저 한다. */
public interface Notices {

    /** 멤버가 아직 확인하지 않은 매장 공지 수. */
    long countUnread(Long storeId, Long memberId);
}
