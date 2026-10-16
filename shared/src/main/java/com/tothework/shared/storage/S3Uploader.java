package com.tothework.shared.storage;

import com.tothework.shared.error.InvalidRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 프로필/매뉴얼 이미지 전용 업로더. 클라이언트가 보낸 Content-Type을 그대로 신뢰하지 않고,
 * 화이트리스트 검증 + 실제 이미지 디코딩 검증을 거친 뒤에만 S3에 올린다 (위장 파일을 통한 저장형 XSS 방지).
 *
 * <p>업로드 결과로 전체 URL이 아니라 S3 key만 돌려준다. 버킷·리전·CDN 도메인은 언제든 바뀔 수 있는
 * 인프라 설정이라, 이를 DB 행마다 복사해두면 옮길 때 저장된 링크가 전부 죽는다. 저장은 key로 하고
 * 공개 URL은 응답을 만들 때 {@link #toPublicUrl}로 조립한다.
 */
public class S3Uploader {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    /**
     * 브라우저가 이 이미지를 다시 받지 않도록 오래 캐시하게 한다.
     *
     * <p>이렇게 둘 수 있는 근거는 키에 UUID가 들어가 <b>같은 키를 다시 쓰는 경우가 없다는 것</b>이다.
     * 사진을 바꾸면 새 키로 올리고 DB가 그쪽을 가리키므로, 낡은 내용이 캐시에 남아 보일 수 없다.
     *
     * <p>이 헤더가 없으면 S3도 캐시 지시를 주지 않아, 브라우저가 이미지마다 매번 서버에 물어본다.
     * 내용은 안 받아도(304) 왕복은 그대로라, 멤버 목록처럼 사진이 여럿인 화면에서 그만큼 요청이 는다.
     */
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public S3Uploader(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucketName = properties.bucketName();
        this.region = properties.region();
    }

    public String upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("업로드할 파일이 비어 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidRequestException("이미지 파일(JPEG/PNG/GIF)만 업로드할 수 있습니다.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("파일을 읽을 수 없습니다.");
        }
        return store(content, contentType, file.getOriginalFilename(), directory);
    }

    /**
     * 검증을 거쳐 실제로 저장한다. 바이트를 이미 손에 쥔 경로(소셜 로그인 프로필 사진 가져오기)도
     * 같은 검증을 통과하도록 이 메서드를 함께 쓴다 — 검증이 업로드 경로마다 갈라지면 한쪽만
     * 느슨해진다.
     */
    public String store(byte[] content, String contentType, String originalFilename, String directory) {
        if (content == null || content.length == 0) {
            throw new InvalidRequestException("업로드할 파일이 비어 있습니다.");
        }
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidRequestException("이미지 파일(JPEG/PNG/GIF)만 업로드할 수 있습니다.");
        }
        try {
            if (ImageIO.read(new ByteArrayInputStream(content)) == null) {
                throw new InvalidRequestException("올바른 이미지 파일이 아닙니다.");
            }
        } catch (IOException e) {
            throw new InvalidRequestException("파일을 읽을 수 없습니다.");
        }

        String key = directory + "/" + UUID.randomUUID() + "-" + sanitizeFilename(originalFilename);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(IMMUTABLE_CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(content));
        return key;
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    /** 저장된 key를 클라이언트가 바로 쓸 수 있는 공개 URL로 만든다. CDN을 붙이면 이 메서드만 바꾸면 된다. */
    public String toPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucketName, region, key);
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return "file";
        }
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
