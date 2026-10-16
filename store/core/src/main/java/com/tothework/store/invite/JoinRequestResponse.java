package com.tothework.store.invite;

import com.tothework.store.api.MemberRole;
import java.time.LocalDateTime;

public record JoinRequestResponse(
        Long id,
        Long storeId,
        String storeName,
        String storeProfileImageUrl,
        Long userId,
        String userName,
        String userEmail,
        String userProfileImageUrl,
        JoinRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        MemberRole decidedRole) {
}
