package com.pte.media.internal.service;

import com.pte.media.domain.MediaObject;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.internal.dto.request.CloudinaryCompleteRequest;
import com.pte.media.internal.dto.request.CloudinaryUploadRequest;
import com.pte.media.internal.dto.response.CloudinaryUploadResponse;
import com.pte.media.internal.dto.response.MediaPreviewResponse;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.exception.MediaAlreadyUploadedException;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.exception.MediaNotYetUploadedException;
import com.pte.media.internal.exception.UnsupportedContentTypeException;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.repository.MediaObjectRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

/** Cloudinary direct-upload adapter for platform authoring media. */
@Service
public class CloudinaryMediaService {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<String> AUDIO_TYPES = Set.of("audio/wav");
    private static final long SIGNATURE_TTL_SECONDS = 900;

    private final MediaObjectRepository repository;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryMediaService(MediaObjectRepository repository,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.authoring-folder:pte/authoring}") String folder) {
        this.repository = repository;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = folder;
    }

    @Transactional
    public CloudinaryUploadResponse requestUpload(CloudinaryUploadRequest request, CurrentUser caller) {
        if (!caller.hasRole("PLATFORM_ADMIN") && !caller.hasRole("PLATFORM_AUTHOR")) {
            throw new AccessDeniedException("Only platform authors may upload question media");
        }
        validateContentType(request.contentType(), request.assetKind());
        if (request.sizeBytes() == null || request.sizeBytes() <= 0
                || request.sizeBytes() > MediaConstants.MAX_AUTHORING_BYTES) {
            throw new UnsupportedContentTypeException();
        }
        long timestamp = Instant.now().getEpochSecond();
        String resourceType = IMAGE_TYPES.contains(request.contentType()) ? "image" : "video";
        UUID mediaPublicId = UUID.randomUUID();
        String publicId = mediaPublicId.toString();
        String cloudinaryPublicId = folder + "/" + publicId;
        String signature = sign("folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp);

        MediaObject media = new MediaObject();
        media.setPublicId(mediaPublicId);
        media.setTenantId(caller.tenantId());
        media.setOwnerPublicId(caller.userId());
        media.setContentType(request.contentType());
        media.setAudioPrompt("AUDIO_PROMPT".equals(request.assetKind()));
        media.setStorageKey(cloudinaryPublicId);
        media.setCloudinaryPublicId(cloudinaryPublicId);
        media.setCloudinaryResourceType(resourceType);
        media.setSizeBytes(request.sizeBytes());
        repository.save(media);

        String uploadUrl = "https://api.cloudinary.com/v1_1/%s/%s/upload".formatted(cloudName, resourceType);
        return new CloudinaryUploadResponse(media.getPublicId(), uploadUrl, apiKey, String.valueOf(timestamp),
                signature, folder, resourceType, SIGNATURE_TTL_SECONDS);
    }

    @Transactional
    public void completeUpload(UUID mediaPublicId, CloudinaryCompleteRequest request, CurrentUser caller) {
        MediaObject media = repository.findByPublicId(mediaPublicId)
                .filter(candidate -> candidate.getOwnerPublicId().equals(caller.userId()))
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() == MediaStatus.UPLOADED) {
            throw new MediaAlreadyUploadedException();
        }
        if (!request.publicId().equals(media.getCloudinaryPublicId())
                || !request.resourceType().equals(media.getCloudinaryResourceType())
                || !request.secureUrl().startsWith("https://res.cloudinary.com/")
                || request.version() == null
                || request.signature() == null
                || !MessageDigest.isEqual(
                        sign("public_id=" + request.publicId() + "&version=" + request.version())
                                .getBytes(StandardCharsets.US_ASCII),
                        request.signature().getBytes(StandardCharsets.US_ASCII))) {
            throw new MediaNotFoundException();
        }
        if (request.bytes() == null || request.bytes() <= 0 || request.bytes() > media.getSizeBytes()) {
            throw new MediaNotFoundException();
        }
        media.setAssetId(request.assetId());
        media.setSecureUrl(request.secureUrl());
        media.setSizeBytes(request.bytes());
        media.setDurationSeconds(request.durationSeconds());
        media.markUploaded();
    }

    @Transactional(readOnly = true)
    public MediaPreviewResponse preview(UUID mediaPublicId, CurrentUser caller) {
        MediaObject media = repository.findByPublicId(mediaPublicId)
                .filter(candidate -> candidate.getOwnerPublicId().equals(caller.userId()) || caller.hasRole("PLATFORM_ADMIN"))
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() != MediaStatus.UPLOADED || media.getSecureUrl() == null) {
            throw new MediaNotYetUploadedException();
        }
        return new MediaPreviewResponse(media.getSecureUrl(), SIGNATURE_TTL_SECONDS, media.getDurationSeconds());
    }

    /** Trusted module-to-module resolution for approved question media. */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse resolveForTrustedCaller(UUID mediaPublicId, long requestedTtlSeconds,
            UUID tenantId) {
        Optional<MediaObject> mediaCandidate = repository.findByPublicId(mediaPublicId);
        if (mediaCandidate.isEmpty()) {
            return null;
        }
        MediaObject media = mediaCandidate.get();
        if (media.getCloudinaryPublicId() == null) {
            return null;
        }
        if (media.getTenantId() != null && !media.getTenantId().equals(tenantId)) {
            throw new MediaNotFoundException();
        }
        if (media.getStatus() != MediaStatus.UPLOADED || media.getSecureUrl() == null) {
            throw new MediaNotYetUploadedException();
        }
        long ttl = Math.min(Math.max(requestedTtlSeconds, 1), SIGNATURE_TTL_SECONDS);
        return new PresignedDownloadResponse(media.getSecureUrl(), ttl, media.getDurationSeconds());
    }

    public void validateAuthoringMedia(UUID mediaPublicId, String assetKind, CurrentUser caller) {
        MediaObject media = repository.findByPublicId(mediaPublicId).orElseThrow(MediaNotFoundException::new);
        if (media.getCloudinaryPublicId() == null || media.getStatus() != MediaStatus.UPLOADED) {
            throw new MediaNotYetUploadedException();
        }
        boolean audio = "AUDIO_PROMPT".equals(assetKind);
        boolean contentTypeAllowed = audio ? AUDIO_TYPES.contains(media.getContentType())
                : "IMAGE_PROMPT".equals(assetKind) && IMAGE_TYPES.contains(media.getContentType());
        if (!contentTypeAllowed || media.isAudioPrompt() != audio) {
            throw new UnsupportedContentTypeException();
        }
        if (!caller.hasRole("PLATFORM_ADMIN") && !media.getOwnerPublicId().equals(caller.userId())) {
            throw new AccessDeniedException("The media asset is owned by another author");
        }
    }

    private void validateContentType(String contentType, String assetKind) {
        boolean allowed = "IMAGE_PROMPT".equals(assetKind) ? IMAGE_TYPES.contains(contentType)
                : "AUDIO_PROMPT".equals(assetKind) && AUDIO_TYPES.contains(contentType);
        if (!allowed) {
            throw new UnsupportedContentTypeException();
        }
    }

    private String sign(String payload) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("Cloudinary credentials are not configured");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest((payload + apiSecret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is unavailable", ex);
        }
    }
}
