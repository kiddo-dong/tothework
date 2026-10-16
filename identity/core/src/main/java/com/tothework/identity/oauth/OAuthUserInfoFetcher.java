package com.tothework.identity.oauth;

import com.tothework.identity.user.AuthProvider;

/** provider가 발급한 access token으로 provider의 userinfo API를 호출해 사용자 정보를 조회한다. */
public interface OAuthUserInfoFetcher {

    AuthProvider getProvider();

    OAuthUserInfo fetch(String accessToken);

    /**
     * JsonNode.asText()는 값이 없을 때 빈 문자열을 준다. 사진을 등록하지 않은 것과 빈 주소를
     * 구분해야 하므로 없음은 null로 통일한다.
     */
    default String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
