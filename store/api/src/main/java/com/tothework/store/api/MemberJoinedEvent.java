package com.tothework.store.api;

/**
 * 누군가 매장에 합류했다 (매장 개설자 포함). workforce는 이걸 받아 기본 고용 조건을 만든다.
 *
 * @param rejoined 퇴사했다 돌아온 경우. 예전 기록을 이어 쓰므로 고용 조건을 새로 만들지 않는다
 */
public record MemberJoinedEvent(Long memberId, Long storeId, Long userId, MemberRole role, boolean rejoined) {
}
