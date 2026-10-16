package com.tothework.identity.oauth;

import com.tothework.shared.error.InvalidRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
class OAuthApiClient {

    private final RestClient restClient = RestClient.create();

    JsonNode getUserInfo(String uri, String accessToken) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new InvalidRequestException("유효하지 않은 액세스 토큰입니다.");
        }
    }
}
