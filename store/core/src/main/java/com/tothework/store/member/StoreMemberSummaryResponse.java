package com.tothework.store.member;

import com.tothework.store.api.MemberRole;

/** 멤버 요약 (이름·역할만). 시급·연락처 등 개인정보 없이 동료에게 공개해도 되는 최소 정보. */
public record StoreMemberSummaryResponse(
        Long id,
        String userName,
        /** 프로필 사진 공개 URL. 등록하지 않았으면 null. */
        String userProfileImageUrl,
        MemberRole role,
        /** 매장이 부르는 직함. 안 정했으면 null. */
        String title) {
}
