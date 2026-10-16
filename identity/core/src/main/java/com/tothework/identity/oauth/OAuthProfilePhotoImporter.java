package com.tothework.identity.oauth;

import com.tothework.shared.storage.S3Uploader;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 소셜 로그인 제공자가 준 프로필 사진을 내려받아 우리 저장소에 둔다.
 *
 * <p>주소를 그대로 저장하지 않는 이유는 두 가지다. 카카오·네이버가 주는 주소는 영구적이지 않아
 * 언젠가 사진이 깨지고, 우리 응답은 저장된 S3 key로 URL을 조립하므로 남의 주소를 그 자리에 넣으면
 * 형식이 어긋난다. 직접 올린 사진과 같은 곳에 두면 두 문제가 함께 사라진다.
 *
 * <p><b>실패해도 예외를 던지지 않는다.</b> 사진은 있으면 좋은 것이고, 남의 서버가 느리거나 응답이
 * 이상하다고 해서 가입이 막히면 안 된다. 그런 경우 사진 없이 가입되고 사용자가 직접 올리면 된다.
 */
@Slf4j
@Component
public class OAuthProfilePhotoImporter {

    private static final String DIRECTORY = "profile-images";
    /** 프로필 사진치고 큰 값. 이보다 크면 프로필 사진이 아니라고 보고 버린다. */
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private final S3Uploader s3Uploader;
    private final RestClient restClient;

    public OAuthProfilePhotoImporter(S3Uploader s3Uploader) {
        // 기본 설정에는 타임아웃이 없다. 제공자가 응답하지 않으면 가입 요청이 그만큼 매달리므로
        // 짧게 끊는다 — 사진을 못 받는 것보다 가입이 멈추는 쪽이 훨씬 나쁘다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.s3Uploader = s3Uploader;
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 저장한 S3 key. 주소가 없거나 가져오지 못하면 null. */
    public String importFrom(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            ResponseEntity<byte[]> response = restClient.get().uri(imageUrl).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                log.warn("소셜 프로필 사진을 건너뛴다: 크기가 맞지 않음 ({}바이트)",
                        body == null ? 0 : body.length);
                return null;
            }
            MediaType contentType = response.getHeaders().getContentType();
            // 확장자 없는 주소가 흔해 파일명 대신 응답의 Content-Type을 믿는다. 다만 그 값이
            // 정말 이미지인지는 S3Uploader가 실제로 디코딩해 다시 확인한다.
            String type = contentType == null ? null
                    : contentType.getType() + "/" + contentType.getSubtype();
            return s3Uploader.store(body, type, "social-profile", DIRECTORY);
        } catch (RuntimeException e) {
            log.warn("소셜 프로필 사진을 가져오지 못해 사진 없이 진행한다: {}", e.toString());
            return null;
        }
    }
}
