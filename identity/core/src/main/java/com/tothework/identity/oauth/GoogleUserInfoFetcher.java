package com.tothework.identity.oauth;

import com.tothework.identity.user.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class GoogleUserInfoFetcher implements OAuthUserInfoFetcher {

    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final OAuthApiClient oAuthApiClient;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo fetch(String accessToken) {
        JsonNode body = oAuthApiClient.getUserInfo(USERINFO_URI, accessToken);
        return new OAuthUserInfo(body.path("sub").asText(), body.path("email").asText(),
                body.path("name").asText(), emptyToNull(body.path("picture").asText()));
    }
}
