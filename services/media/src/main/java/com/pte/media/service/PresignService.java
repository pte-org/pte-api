package com.pte.media.service;

import com.pte.common.security.CurrentUser;
import com.pte.media.constant.MediaConstants;
import com.pte.media.domain.MediaObject;
import com.pte.media.domain.exception.MediaAlreadyUploadedException;
import com.pte.media.domain.exception.MediaNotFoundException;
import com.pte.media.domain.exception.MediaNotYetUploadedException;
import com.pte.media.domain.exception.PresignFailedException;
import com.pte.media.domain.exception.UnsupportedContentTypeException;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.dto.request.RequestUploadRequest;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.dto.response.RequestUploadResponse;
import com.pte.media.repository.MediaObjectRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
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
    /** Caps a caller-requested download TTL regardless of what it asks for — no indefinitely-valid presigned URL. */
    private static final long MAX_DOWNLOAD_URL_TTL_SECONDS = 24 * 60 * 60;

    private final MediaObjectRepository mediaObjectRepository;
    // Presigning only — never used for a real network call (see
    // MinioConfig.presignMinioClient's doc comment for why this must be a
    // separate, externally-reachable-endpoint client from the one used for
    // this service's own server-to-server MinIO calls elsewhere).
    private final MinioClient presignMinioClient;
    private final String bucket;

    public PresignService(MediaObjectRepository mediaObjectRepository,
                          @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                          @Value("${media.storage.bucket}") String bucket) {
        this.mediaObjectRepository = mediaObjectRepository;
        this.presignMinioClient = presignMinioClient;
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

    /**
     * Internal service-to-service surface only (see {@code InternalMediaController}).
     * Tenant-scoped at the database layer (not just trusted from the caller) —
     * the calling service's own entitlement check is a separate, upper-layer
     * guard; this lookup must not rely on it alone (defense in depth, red-team
     * finding from Phase 6 quality gate).
     */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse presignGet(UUID mediaPublicId, long requestedTtlSeconds, UUID tenantId) {
        MediaObject media = mediaObjectRepository.findByPublicIdAndTenantId(mediaPublicId, tenantId)
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() != MediaStatus.UPLOADED) {
            throw new MediaNotYetUploadedException();
        }
        long ttlSeconds = Math.min(requestedTtlSeconds, MAX_DOWNLOAD_URL_TTL_SECONDS);
        String url = presignGetUrl(media.getStorageKey(), ttlSeconds);
        return new PresignedDownloadResponse(url, ttlSeconds);
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
            return presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(storageKey)
                    .expiry(UPLOAD_URL_TTL_SECONDS, TimeUnit.SECONDS)
                    .build());
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw new PresignFailedException();
        }
    }

    private String presignGetUrl(String storageKey, long ttlSeconds) {
        try {
            return presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(storageKey)
                    .expiry((int) ttlSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw new PresignFailedException();
        }
    }
}
