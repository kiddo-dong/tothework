package com.tothework.identity.user;

import com.tothework.shared.security.AuthenticatedUser;

/** 인증된 요청의 주체. 인증에 필요한 값만 담고 엔티티는 들고 있지 않는다. */
record IdentityPrincipal(Long userId, boolean profileCompleted) implements AuthenticatedUser {
}
