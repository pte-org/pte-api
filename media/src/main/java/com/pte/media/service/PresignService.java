package com.pte.media.service;

import com.pte.common.security.CurrentUser;
import com.pte.media.constant.MediaConstants;
import com.pte.media.domain.MediaObject;
import com.pte.media.domain.exception.MediaAlreadyUploadedException;
import com.pte.media.domain.exception.MediaNotFoundException;
import com.pte.media.domain.exception.PresignFailedException;
import com.pte.media.domain.exception.UnsupportedContentTypeException;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.dto.request.RequestUploadRequest;
import com.pte.media.dto.response.RequestUploadResponse;
import com.pte.media.repository.MediaObjectRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Presigned upload for student audio (Read Aloud). Short-TTL PUT URL — the
 * student's browser/app uploads DIRECTLY to MinIO, never through this service's
 * own request body (avoids proxying large binaries through the API tier).
 */
@Service
public class PresignService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaConstants.AUDIO_MPEG, MediaConstants.AUDIO_WAV, MediaConstants.AUDIO_WEBM);
    private static final int UPLOAD_URL_TTL_SECONDS = 15 * 60;

    private final MediaObjectRepository mediaObjectRepository;
    private final MinioClient minioClient;
    private final String bucket;

    public PresignService(MediaObjectRepository mediaObjectRepository, MinioClient minioClient,
                          @Value("${media.storage.bucket}") String bucket) {
        this.mediaObjectRepository = mediaObjectRepository;
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Transactional
    public RequestUploadResponse requestUpload(RequestUploadRequest request, CurrentUser caller) {
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
            throw new UnsupportedContentTypeException();
        }

        MediaObject media = new MediaObject();
        media.setTenantId(caller.tenantId());
        media.setOwnerPublicId(caller.userId());
        media.setContentType(request.contentType());
        UUID publicIdSeed = UUID.randomUUID();
        media.setStorageKey(buildStorageKey(caller, publicIdSeed, request.contentType()));
        MediaObject saved = mediaObjectRepository.save(media);

        String uploadUrl = presignPut(saved.getStorageKey());
        return new RequestUploadResponse(saved.getPublicId(), uploadUrl, UPLOAD_URL_TTL_SECONDS);
    }

    @Transactional
    public void completeUpload(UUID mediaPublicId, CurrentUser caller) {
        MediaObject media = mediaObjectRepository.findByPublicIdAndTenantId(mediaPublicId, caller.tenantId())
                .filter(m -> m.getOwnerPublicId().equals(caller.userId()))
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() == MediaStatus.UPLOADED) {
            throw new MediaAlreadyUploadedException();
        }
        media.markUploaded();
        mediaObjectRepository.save(media);
    }

    private String buildStorageKey(CurrentUser caller, UUID seed, String contentType) {
        String extension = contentType.substring(contentType.indexOf('/') + 1);
        return "audio/%s/%s.%s".formatted(caller.tenantId(), seed, extension);
    }

    private String presignPut(String storageKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(storageKey)
                    .expiry(UPLOAD_URL_TTL_SECONDS, TimeUnit.SECONDS)
                    .build());
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw new PresignFailedException();
        }
    }
}
