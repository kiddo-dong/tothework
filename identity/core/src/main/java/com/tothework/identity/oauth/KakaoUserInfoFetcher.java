package com.tothework.identity.oauth;

import com.tothework.identity.user.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class KakaoUserInfoFetcher implements OAuthUserInfoFetcher {

    private static final String USERINFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final OAuthApiClient oAuthApiClient;

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetch(String accessToken) {
        JsonNode body = oAuthApiClient.getUserInfo(USERINFO_URI, accessToken);
        JsonNode kakaoAccount = body.path("kakao_account");
        String email = kakaoAccount.path("email").asText();
        String name = kakaoAccount.path("profile").path("nickname").asText();
        String profileImageUrl = kakaoAccount.path("profile").path("profile_image_url").asText();
        return new OAuthUserInfo(body.path("id").asText(), email, name,
                emptyToNull(profileImageUrl));
    }
}
