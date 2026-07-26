package com.pte.media.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MinIO client + bucket bootstrap (idempotent — safe on every restart). */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(@Value("${media.storage.endpoint}") String endpoint,
                                   @Value("${media.storage.access-key}") String accessKey,
                                   @Value("${media.storage.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ApplicationRunner ensureBucketExists(MinioClient minioClient,
                                                @Value("${media.storage.bucket}") String bucket) {
        return (ApplicationArguments args) -> {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        };
    }
}
