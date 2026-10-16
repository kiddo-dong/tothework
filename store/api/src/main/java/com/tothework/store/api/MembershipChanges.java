package com.tothework.store.api;

/** 멤버 수정 요청 중 store가 맡는 부분. null인 항목은 바꾸지 않는다. 직함은 빈 문자열이면 지운다. */
public record MembershipChanges(MemberRole role, String title, MemberStatus status) {

    public boolean isEmpty() {
        return role == null && title == null && status == null;
    }
}
