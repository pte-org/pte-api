package com.pte.media.internal.service;

import com.pte.media.domain.MediaObject;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.internal.dto.request.CloudinaryCompleteRequest;
import com.pte.media.internal.dto.request.CloudinaryUploadRequest;
import com.pte.media.internal.dto.response.CloudinaryUploadResponse;
import com.pte.media.internal.dto.response.MediaPreviewResponse;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.exception.MediaNotYetUploadedException;
import com.pte.media.internal.exception.UnsupportedContentTypeException;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.repository.MediaObjectRepository;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.practice.PracticeMediaConstants;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityCapability;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Cloudinary direct-upload adapter for authoring media and student responses. */
@Service
public class CloudinaryMediaService implements PracticeResponseMediaValidator {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Set<String> AUDIO_TYPES = Set.of("audio/wav");
    private static final long SIGNATURE_TTL_SECONDS = 900;

    private final MediaObjectRepository repository;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String authoringFolder;
    private final String submissionFolder;
    private final PracticeMediaBindingPort practiceMediaBindingService;
    private final AuditLogService auditLogService;

    @Autowired
    public CloudinaryMediaService(MediaObjectRepository repository,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.authoring-folder:pte/authoring}") String authoringFolder,
            @Value("${cloudinary.submission-folder:pte/submissions}") String submissionFolder,
            PracticeMediaBindingPort practiceMediaBindingService, AuditLogService auditLogService) {
        this(repository, cloudName, apiKey, apiSecret, authoringFolder, submissionFolder,
                practiceMediaBindingService, auditLogService, true);
    }

    /** Kept for focused media tests and legacy callers that do not exercise practice binding. */
    public CloudinaryMediaService(MediaObjectRepository repository, String cloudName, String apiKey,
            String apiSecret, String authoringFolder, String submissionFolder) {
        this(repository, cloudName, apiKey, apiSecret, authoringFolder, submissionFolder, null, null, false);
    }

    /** Compatibility constructor for tests/callers that provide practice binding only. */
    public CloudinaryMediaService(MediaObjectRepository repository, String cloudName, String apiKey,
            String apiSecret, String authoringFolder, String submissionFolder,
            PracticeMediaBindingPort practiceMediaBindingService) {
        this(repository, cloudName, apiKey, apiSecret, authoringFolder, submissionFolder,
                practiceMediaBindingService, null, false);
    }

    private CloudinaryMediaService(MediaObjectRepository repository, String cloudName, String apiKey,
            String apiSecret, String authoringFolder, String submissionFolder,
            PracticeMediaBindingPort practiceMediaBindingService, AuditLogService auditLogService, boolean ignored) {
        this.repository = repository;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.authoringFolder = authoringFolder;
        this.submissionFolder = submissionFolder;
        this.practiceMediaBindingService = practiceMediaBindingService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CloudinaryUploadResponse requestUpload(CloudinaryUploadRequest request, CurrentUser caller) {
        boolean authoringMedia = isAuthoringAssetKind(request.assetKind());
        boolean studentResponse = MediaConstants.STUDENT_RESPONSE_AUDIO.equals(request.assetKind());
        if (authoringMedia && !isPlatformAuthor(caller)) {
            deny(caller, MediaConstants.ACADEMIC_DRAFT_WRITE_REQUIRED_FOR_QUESTION_MEDIA);
        }
        if (studentResponse && !caller.hasRole(SecurityRoles.STUDENT)) {
            deny(caller, MediaConstants.STUDENT_REQUIRED_FOR_RESPONSE_AUDIO);
        }
        if (!authoringMedia && !studentResponse) {
            throw new UnsupportedContentTypeException();
        }
        if (studentResponse) {
            requirePracticeBinding(request);
            practiceMediaBindingService.assertCanUseResponseAudio(
                    request.practiceSessionId(), request.practiceItemId(), caller);
        }
        validateContentType(request.contentType(), request.assetKind());
        long maxBytes = studentResponse ? MediaConstants.MAX_PRACTICE_RESPONSE_BYTES
                : MediaConstants.MAX_AUTHORING_BYTES;
        if (request.sizeBytes() == null || request.sizeBytes() <= 0
                || request.sizeBytes() > maxBytes) {
            throw new UnsupportedContentTypeException();
        }
        long timestamp = Instant.now().getEpochSecond();
        String resourceType = IMAGE_TYPES.contains(request.contentType()) ? "image" : "video";
        UUID mediaPublicId = UUID.randomUUID();
        String publicId = mediaPublicId.toString();
        String folder = studentResponse ? submissionFolder : authoringFolder;
        String cloudinaryPublicId = folder + "/" + publicId;
        // Cloudinary defaults to the public `upload` delivery type. Keep the
        // signed upload small and let public media use its stable secure URL.
        String signature = sign("folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp);

        MediaObject media = new MediaObject();
        media.setPublicId(mediaPublicId);
        media.setTenantId(caller.tenantId());
        media.setOwnerPublicId(caller.userId());
        media.setContentType(request.contentType());
        media.setAudioPrompt(MediaConstants.AUDIO_PROMPT.equals(request.assetKind()));
        media.setStorageKey(cloudinaryPublicId);
        media.setCloudinaryPublicId(cloudinaryPublicId);
        media.setCloudinaryResourceType(resourceType);
        media.setCloudinaryDeliveryType(CloudinaryDeliveryType.UPLOAD);
        media.setSizeBytes(request.sizeBytes());
        if (studentResponse) {
            media.setPracticeSessionPublicId(request.practiceSessionId());
            media.setPracticeItemPublicId(request.practiceItemId());
            media.setPurpose(request.purpose());
        }
        repository.save(media);

        String uploadUrl = "https://api.cloudinary.com/v1_1/%s/%s/upload".formatted(cloudName, resourceType);
        return new CloudinaryUploadResponse(media.getPublicId(), cloudinaryPublicId, uploadUrl, apiKey,
                String.valueOf(timestamp),
                signature, folder, resourceType, SIGNATURE_TTL_SECONDS);
    }

    @Transactional
    public void completeUpload(UUID mediaPublicId, CloudinaryCompleteRequest request, CurrentUser caller) {
        MediaObject media = repository.findByPublicId(mediaPublicId)
                .filter(candidate -> candidate.getOwnerPublicId().equals(caller.userId()))
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() == MediaStatus.UPLOADED) {
            // Completion is intentionally idempotent. A mobile process may
            // be killed after pte-api commits the completion but before the
            // local upload row can be marked ready.
            return;
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
        if (isPracticeResponseAudio(media)
                && (request.durationSeconds() == null
                        || request.durationSeconds() <= 0
                        || request.durationSeconds() > PracticeMediaConstants.MAX_RESPONSE_DURATION_SECONDS)) {
            throw new MediaNotFoundException();
        }
        if (isPracticeResponseAudio(media)) {
            requirePracticeBinding(media, caller);
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
        long ttl = media.getCloudinaryDeliveryType() == CloudinaryDeliveryType.AUTHENTICATED
                ? SIGNATURE_TTL_SECONDS : 0;
        String url = ttl == 0 ? media.getSecureUrl() : privateDownloadUrl(media, ttl);
        return new MediaPreviewResponse(url, ttl, media.getDurationSeconds());
    }

    /** Trusted module-to-module resolution for approved question media. */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse resolveForTrustedCaller(UUID mediaPublicId, long requestedTtlSeconds,
            UUID tenantId) {
        if (mediaPublicId == null) {
            throw new MediaNotFoundException();
        }
        return resolveForTrustedCallers(List.of(mediaPublicId), requestedTtlSeconds, tenantId).get(mediaPublicId);
    }

    /** Resolves an authorized caller's media set with one repository query. */
    @Transactional(readOnly = true)
    public Map<UUID, PresignedDownloadResponse> resolveForTrustedCallers(
            Collection<UUID> mediaPublicIds, long requestedTtlSeconds, UUID tenantId) {
        if (mediaPublicIds == null || mediaPublicIds.isEmpty()) {
            return Map.of();
        }
        if (tenantId == null) {
            throw new MediaNotFoundException();
        }
        List<UUID> requestedIds = mediaPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, MediaObject> mediaByPublicId = new HashMap<>();
        repository.findAllForTrustedTenant(requestedIds, tenantId)
                .forEach(media -> mediaByPublicId.put(media.getPublicId(), media));
        Map<UUID, PresignedDownloadResponse> resolved = new HashMap<>();
        for (UUID mediaPublicId : requestedIds) {
            MediaObject media = mediaByPublicId.get(mediaPublicId);
            if (media == null) {
                throw new MediaNotFoundException();
            }
            resolved.put(mediaPublicId, toTrustedDownloadResponse(media, requestedTtlSeconds, tenantId));
        }
        return Map.copyOf(resolved);
    }

    private PresignedDownloadResponse toTrustedDownloadResponse(
            MediaObject media, long requestedTtlSeconds, UUID tenantId) {
        if (media.getCloudinaryPublicId() == null) {
            throw new MediaNotFoundException();
        }
        if (media.getTenantId() != null && !media.getTenantId().equals(tenantId)) {
            throw new MediaNotFoundException();
        }
        if (media.getStatus() != MediaStatus.UPLOADED || media.getSecureUrl() == null) {
            throw new MediaNotYetUploadedException();
        }
        long ttl = Math.min(Math.max(requestedTtlSeconds, 1), SIGNATURE_TTL_SECONDS);
        if (media.getCloudinaryDeliveryType() != CloudinaryDeliveryType.AUTHENTICATED) {
            // Existing pre-migration assets are still public Cloudinary uploads; never claim their URL expires.
            return new PresignedDownloadResponse(media.getSecureUrl(), 0, media.getDurationSeconds());
        }
        return new PresignedDownloadResponse(privateDownloadUrl(media, ttl), ttl, media.getDurationSeconds());
    }

    private String privateDownloadUrl(MediaObject media, long ttlSeconds) {
        long timestamp = Instant.now().getEpochSecond();
        Map<String, String> signedParameters = new TreeMap<>();
        signedParameters.put("attachment", "false");
        signedParameters.put("expires_at", String.valueOf(timestamp + ttlSeconds));
        signedParameters.put("format", fileFormat(media.getContentType()));
        signedParameters.put("public_id", media.getCloudinaryPublicId());
        signedParameters.put("timestamp", String.valueOf(timestamp));
        signedParameters.put("type", CloudinaryDeliveryType.AUTHENTICATED.name().toLowerCase(java.util.Locale.ROOT));
        String payload = signedParameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        String signature = sign(payload);
        String query = signedParameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return "https://api.cloudinary.com/v1_1/%s/%s/download?%s&signature=%s&api_key=%s".formatted(
                cloudName, media.getCloudinaryResourceType(), query, signature, encode(apiKey));
    }

    private String fileFormat(String contentType) {
        return switch (contentType) {
            case "audio/wav" -> "wav";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new UnsupportedContentTypeException();
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public void validateAuthoringMedia(UUID mediaPublicId, String assetKind, CurrentUser caller) {
        MediaObject media = repository.findByPublicId(mediaPublicId).orElseThrow(MediaNotFoundException::new);
        if (media.getCloudinaryPublicId() == null || media.getStatus() != MediaStatus.UPLOADED) {
            throw new MediaNotYetUploadedException();
        }
        boolean audio = MediaConstants.AUDIO_PROMPT.equals(assetKind);
        boolean contentTypeAllowed = audio ? AUDIO_TYPES.contains(media.getContentType())
                : MediaConstants.IMAGE_PROMPT.equals(assetKind) && IMAGE_TYPES.contains(media.getContentType());
        if (!contentTypeAllowed || media.isAudioPrompt() != audio) {
            throw new UnsupportedContentTypeException();
        }
        if (!caller.hasRole(SecurityRoles.PLATFORM_ADMIN) && !media.getOwnerPublicId().equals(caller.userId())) {
            deny(caller, MediaConstants.MEDIA_ASSET_OWNED_BY_ANOTHER_AUTHOR);
        }
    }

    /** Validates that an uploaded recording still belongs to the live practice item. */
    @Transactional(readOnly = true)
    public void validatePracticeResponseAudio(UUID mediaPublicId, UUID sessionPublicId,
            UUID itemPublicId, CurrentUser caller) {
        if (mediaPublicId == null || sessionPublicId == null || itemPublicId == null || caller == null
                || caller.userId() == null) {
            throw new MediaNotFoundException();
        }
        MediaObject media = repository.findByPublicId(mediaPublicId)
                .filter(candidate -> candidate.getOwnerPublicId().equals(caller.userId()))
                .orElseThrow(MediaNotFoundException::new);
        if (!isPracticeResponseAudio(media)
                || media.getStatus() != MediaStatus.UPLOADED
                || !sessionPublicId.equals(media.getPracticeSessionPublicId())
                || !itemPublicId.equals(media.getPracticeItemPublicId())
                || !MediaConstants.AUDIO_WAV.equals(media.getContentType())
                || media.getSizeBytes() == null
                || media.getSizeBytes() <= 0
                || media.getSizeBytes() > MediaConstants.MAX_PRACTICE_RESPONSE_BYTES
                || media.getDurationSeconds() == null
                || media.getDurationSeconds() <= 0
                || media.getDurationSeconds() > PracticeMediaConstants.MAX_RESPONSE_DURATION_SECONDS) {
            throw new MediaNotFoundException();
        }
        requirePracticeBinding(media, caller);
    }

    private void validateContentType(String contentType, String assetKind) {
        boolean allowed = MediaConstants.IMAGE_PROMPT.equals(assetKind) ? IMAGE_TYPES.contains(contentType)
                : (MediaConstants.AUDIO_PROMPT.equals(assetKind)
                        || MediaConstants.STUDENT_RESPONSE_AUDIO.equals(assetKind))
                                && AUDIO_TYPES.contains(contentType);
        if (!allowed) {
            throw new UnsupportedContentTypeException();
        }
    }

    private void requirePracticeBinding(CloudinaryUploadRequest request) {
        if (practiceMediaBindingService == null
                || request.practiceSessionId() == null
                || request.practiceItemId() == null
                || !PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE.equals(request.purpose())) {
            throw new UnsupportedContentTypeException();
        }
    }

    private void requirePracticeBinding(MediaObject media, CurrentUser caller) {
        if (practiceMediaBindingService == null
                || media.getPracticeSessionPublicId() == null
                || media.getPracticeItemPublicId() == null
                || !PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE.equals(media.getPurpose())) {
            throw new MediaNotFoundException();
        }
        practiceMediaBindingService.assertCanUseResponseAudio(
                media.getPracticeSessionPublicId(), media.getPracticeItemPublicId(), caller);
    }

    private boolean isPracticeResponseAudio(MediaObject media) {
        return PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE.equals(media.getPurpose());
    }

    private boolean isAuthoringAssetKind(String assetKind) {
        return MediaConstants.IMAGE_PROMPT.equals(assetKind) || MediaConstants.AUDIO_PROMPT.equals(assetKind);
    }

    private boolean isPlatformAuthor(CurrentUser caller) {
        return SecurityPolicy.hasCapability(caller, SecurityCapability.ACADEMIC_DRAFT_WRITE);
    }

    private void deny(CurrentUser caller, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, "MEDIA", "authoring",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
    }

    private String sign(String payload) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException(MediaConstants.CLOUDINARY_CREDENTIALS_NOT_CONFIGURED);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest((payload + apiSecret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(MediaConstants.SHA1_UNAVAILABLE, ex);
        }
    }
}
