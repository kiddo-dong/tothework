package com.tothework.identity.oauth;

import com.tothework.identity.user.AuthProvider;
import com.tothework.shared.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class NaverUserInfoFetcher implements OAuthUserInfoFetcher {

    private static final String USERINFO_URI = "https://openapi.naver.com/v1/nid/me";

    private final OAuthApiClient oAuthApiClient;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.NAVER;
    }

    @Override
    public OAuthUserInfo fetch(String accessToken) {
        JsonNode body = oAuthApiClient.getUserInfo(USERINFO_URI, accessToken);
        if (!"00".equals(body.path("resultcode").asText())) {
            throw new InvalidRequestException("유효하지 않은 액세스 토큰입니다.");
        }
        JsonNode response = body.path("response");
        return new OAuthUserInfo(response.path("id").asText(), response.path("email").asText(),
                response.path("name").asText(),
                emptyToNull(response.path("profile_image").asText()));
    }
}
