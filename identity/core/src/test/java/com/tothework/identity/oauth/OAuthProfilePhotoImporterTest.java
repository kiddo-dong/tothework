package com.tothework.identity.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tothework.shared.storage.S3Uploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 사진 가져오기는 가입을 막지 않아야 한다는 것이 가장 중요한 성질이다. 남의 서버 사정으로
 * 회원가입이 실패하면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthProfilePhotoImporterTest {

    @Mock
    private S3Uploader s3Uploader;

    @Test
    void returnsNothingWhenTheProviderGaveNoPhoto() {
        OAuthProfilePhotoImporter importer = new OAuthProfilePhotoImporter(s3Uploader);

        assertThat(importer.importFrom(null)).isNull();
        assertThat(importer.importFrom("  ")).isNull();
        verify(s3Uploader, never()).store(any(), anyString(), anyString(), anyString());
    }

    /** 주소가 죽었거나 응답이 늦어도 null만 돌려주고 예외를 밖으로 내보내지 않는다. */
    @Test
    void returnsNothingInsteadOfFailingWhenTheDownloadDoesNotWork() {
        OAuthProfilePhotoImporter importer = new OAuthProfilePhotoImporter(s3Uploader);

        String key = importer.importFrom("http://127.0.0.1:1/not-there.png");

        assertThat(key).isNull();
        verify(s3Uploader, never()).store(any(), anyString(), anyString(), anyString());
    }
}
