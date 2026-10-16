package com.tothework.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.tothework.shared.error.InvalidRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3UploaderTest {

    @Mock
    private S3Client s3Client;

    private S3Uploader s3Uploader;

    @BeforeEach
    void setUp() {
        s3Uploader = new S3Uploader(s3Client, new StorageProperties("albam-test-bucket", "ap-northeast-2"));
    }

    @Test
    void upload_rejectsContentTypeOutsideWhitelist() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.svg", "image/svg+xml",
                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> s3Uploader.upload(file, "profile-images"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("JPEG/PNG/GIF");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void upload_rejectsSpoofedContentType_bytesAreNotARealImage() throws IOException {
        // Content-Type은 image/png라고 우기지만 실제 바이트는 HTML이다 (위조 업로드 시나리오).
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "<script>alert('xss')</script>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> s3Uploader.upload(file, "manual-images"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("올바른 이미지");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> s3Uploader.upload(file, "profile-images"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("비어");
    }

    @Test
    void upload_returnsKeyNotFullUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", realPngBytes());

        String key = s3Uploader.upload(file, "profile-images");

        // 호스트가 섞이면 버킷을 옮길 때 DB에 저장된 값이 전부 죽으므로, key만 나와야 한다.
        assertThat(key).startsWith("profile-images/").endsWith("photo.png");
        assertThat(key).doesNotContain("amazonaws.com").doesNotContain("https://");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    /**
     * 이 헤더가 없으면 브라우저가 사진마다 매번 서버에 물어본다. 키가 매번 새로 발급되어 같은
     * 주소의 내용이 바뀌지 않으므로, 길게 캐시해도 낡은 사진이 남지 않는다.
     */
    @Test
    void upload_marksTheImageCacheableForALongTime() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", realPngBytes());

        s3Uploader.upload(file, "profile-images");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(request.getValue().cacheControl()).contains("immutable");
    }

    @Test
    void delete_ignoresNullKey() {
        s3Uploader.delete(null);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_ignoresBlankKey() {
        s3Uploader.delete("   ");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_usesTheKeyDirectly() {
        s3Uploader.delete("profile-images/abc-photo.png");

        verify(s3Client).deleteObject(
                DeleteObjectRequest.builder()
                        .bucket("albam-test-bucket")
                        .key("profile-images/abc-photo.png")
                        .build());
    }

    @Test
    void toPublicUrl_buildsUrlFromBucketAndRegion() {
        String url = s3Uploader.toPublicUrl("profile-images/abc-photo.png");

        assertThat(url)
                .isEqualTo("https://albam-test-bucket.s3.ap-northeast-2.amazonaws.com/profile-images/abc-photo.png");
    }

    @Test
    void toPublicUrl_returnsNullWhenNoImageStored() {
        assertThat(s3Uploader.toPublicUrl(null)).isNull();
    }

    private static byte[] realPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
