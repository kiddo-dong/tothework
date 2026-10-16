package com.tothework.store.invite;

import com.tothework.store.api.MemberRole;
import jakarta.validation.constraints.NotNull;

public record ApproveJoinRequestRequest(@NotNull MemberRole role) {
}
