package com.tothework.identity.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(@NotBlank String accessToken) {
}
