package com.pte.media.service;

import com.pte.common.security.CurrentUser;
import com.pte.media.constant.MediaConstants;
import com.pte.media.domain.MediaObject;
import com.pte.media.domain.exception.InvalidWavFileException;
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
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    // Server-to-server calls with real connectivity to `endpoint` (never the
    // public-facing one) — used here to read an audio-prompt object's own
    // bytes back for WAV duration extraction at complete-upload time
    // (plans/phat-speaking-dynamic-prep-timing). Previously injected nowhere
    // in this class; `MinioConfig`'s bucket-bootstrap runner was its only user.
    private final MinioClient minioClient;
    private final String bucket;

    public PresignService(MediaObjectRepository mediaObjectRepository,
                          @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                          MinioClient minioClient,
                          @Value("${media.storage.bucket}") String bucket) {
        this.mediaObjectRepository = mediaObjectRepository;
        this.presignMinioClient = presignMinioClient;
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Transactional
    public RequestUploadResponse requestUpload(RequestUploadRequest request, CurrentUser caller) {
        boolean audioPrompt = Boolean.TRUE.equals(request.audioPrompt());
        if (audioPrompt) {
            // Narrower, WAV-only allow-list for a Speaking audio prompt —
            // independent of ALLOWED_CONTENT_TYPES below, which stays
            // unchanged for every caller that doesn't opt in (candidates'
            // own recorded answers, any other use of this endpoint).
            if (!MediaConstants.AUDIO_WAV.equals(request.contentType())) {
                throw new UnsupportedContentTypeException();
            }
        } else if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())) {
            throw new UnsupportedContentTypeException();
        }

        MediaObject media = new MediaObject();
        media.setTenantId(caller.tenantId());
        media.setOwnerPublicId(caller.userId());
        media.setContentType(request.contentType());
        media.setAudioPrompt(audioPrompt);
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
        return new PresignedDownloadResponse(url, ttlSeconds, media.getDurationSeconds());
    }

    @Transactional
    public void completeUpload(UUID mediaPublicId, CurrentUser caller) {
        MediaObject media = mediaObjectRepository.findByPublicIdAndTenantId(mediaPublicId, caller.tenantId())
                .filter(m -> m.getOwnerPublicId().equals(caller.userId()))
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() == MediaStatus.UPLOADED) {
            throw new MediaAlreadyUploadedException();
        }
        // Duration extraction happens BEFORE markUploaded()/save() below, and
        // throws (rolling back this whole transaction) rather than returning —
        // an audio-prompt object must never reach UPLOADED without a known
        // duration (spec.md: fail fast, no silent fallback).
        if (media.isAudioPrompt()) {
            media.setDurationSeconds(extractWavDurationSeconds(media.getStorageKey()));
        }
        media.markUploaded();
        mediaObjectRepository.save(media);
    }

    /**
     * Reads the object's own bytes back from storage and determines its exact
     * duration from the WAV header. Only ever called for an
     * {@link MediaObject#isAudioPrompt()} object, whose content type was
     * already restricted to {@code audio/wav} at request-upload time
     * (plans/phat-speaking-dynamic-prep-timing). Package-private (not
     * {@code private}) so tests can stub it directly via {@code Mockito.spy}
     * without needing to fabricate a real MinIO SDK response type.
     */
    int extractWavDurationSeconds(String storageKey) {
        try (InputStream raw = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(storageKey).build())) {
            return parseWavDurationSeconds(raw);
        } catch (MinioException | GeneralSecurityException | IOException ex) {
            throw new InvalidWavFileException();
        }
    }

    /**
     * The actual header-parsing logic (JDK-native {@code javax.sound.sampled}
     * — no estimation, no new dependency), separated from {@link
     * #extractWavDurationSeconds} so it can be unit tested directly against a
     * plain {@link InputStream} (e.g. a {@code ByteArrayInputStream} of real
     * WAV bytes) instead of a mocked MinIO response.
     */
    int parseWavDurationSeconds(InputStream raw) {
        try (BufferedInputStream buffered = new BufferedInputStream(raw)) {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(buffered);
            AudioFormat format = fileFormat.getFormat();
            long frameLength = fileFormat.getFrameLength();
            float frameRate = format.getFrameRate();
            if (frameLength == AudioSystem.NOT_SPECIFIED || frameRate <= 0) {
                throw new InvalidWavFileException();
            }
            return Math.round(frameLength / frameRate);
        } catch (UnsupportedAudioFileException | IOException ex) {
            throw new InvalidWavFileException();
        }
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
