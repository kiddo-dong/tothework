package com.tothework.store.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 소유권 이전 요청. 실수 방지를 위해 매장 이름을 정확히 입력해야 한다. */
public record TransferOwnershipRequest(
        @NotNull Long targetMemberId,
        @NotBlank String confirmName
) {
}
