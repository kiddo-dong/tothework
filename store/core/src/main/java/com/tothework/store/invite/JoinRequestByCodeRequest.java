package com.tothework.store.invite;

import jakarta.validation.constraints.NotBlank;

public record JoinRequestByCodeRequest(@NotBlank String code) {
}
