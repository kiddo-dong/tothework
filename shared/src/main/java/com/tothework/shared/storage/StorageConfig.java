package com.tothework.shared.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    /** 자격 증명은 실행 환경(EC2 인스턴스 역할 등)에서 가져온다. 클라이언트 생성만으로는 접속하지 않는다. */
    @Bean
    S3Client s3Client(StorageProperties properties) {
        return S3Client.builder().region(Region.of(properties.region())).build();
    }

    @Bean
    S3Uploader s3Uploader(S3Client s3Client, StorageProperties properties) {
        return new S3Uploader(s3Client, properties);
    }
}
