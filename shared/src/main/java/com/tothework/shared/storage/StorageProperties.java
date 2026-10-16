package com.tothework.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 이미지 저장소(S3). 버킷·리전은 인프라 설정이라 DB에는 key만 저장하고 URL은 응답 때 조립한다. */
@ConfigurationProperties("tothework.storage.s3")
public record StorageProperties(String bucketName, String region) {
}
