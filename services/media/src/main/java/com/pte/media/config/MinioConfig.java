package com.pte.media.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MinIO client + bucket bootstrap (idempotent — safe on every restart). */
@Configuration
public class MinioConfig {

    /** Server-to-server calls only (bucket bootstrap, etc.) — see the doc comment on `media.storage.endpoint`. */
    @Bean
    public MinioClient minioClient(@Value("${media.storage.endpoint}") String endpoint,
                                   @Value("${media.storage.access-key}") String accessKey,
                                   @Value("${media.storage.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Signs presigned URLs handed back to external callers — see the doc
     * comment on `media.storage.public-endpoint`. Region is pinned explicitly
     * (MinIO's own convention, not a real AWS region, externalized like
     * every other MinIO setting here rather than hardcoded — a real
     * S3-compatible deployment in a genuine AWS region would need this
     * overridden) rather than left to the SDK's auto-detection: without a
     * client-provided region, the MinIO Java SDK resolves it via a real
     * {@code GetBucketLocation} network call on first use per bucket — which
     * this client, configured with the (deliberately host-external) public
     * endpoint, cannot make from inside this container. Pinning the region
     * avoids that call entirely, keeping this client's
     * {@code getPresignedObjectUrl} the pure local HMAC computation it's
     * meant to be. The admin-scoped {@link #minioClient} bean above doesn't
     * need this — it has real connectivity to `endpoint` and auto-detection
     * works fine there (code-reviewer finding, plans/phat-speaking-api-e2e-verify).
     */
    @Bean
    @Qualifier("presignMinioClient")
    public MinioClient presignMinioClient(@Value("${media.storage.public-endpoint}") String publicEndpoint,
                                          @Value("${media.storage.access-key}") String accessKey,
                                          @Value("${media.storage.secret-key}") String secretKey,
                                          @Value("${media.storage.region}") String region) {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .region(region)
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
