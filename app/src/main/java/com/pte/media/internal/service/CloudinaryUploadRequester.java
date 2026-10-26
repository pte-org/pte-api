package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.dto.request.CloudinaryUploadRequest;
import com.pte.media.internal.dto.response.CloudinaryUploadResponse;
import com.pte.media.internal.exception.UnsupportedContentTypeException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.practice.PracticeMediaConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityCapability;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;

import java.time.Clock;
import java.util.UUID;

/** Authorizes a direct upload, reserves its media row and returns the signed Cloudinary upload parameters. */
class CloudinaryUploadRequester {

    private final MediaAssetRepository repository;
    private final CloudinaryRequestSigner signer;
    private final MediaAccessGuard accessGuard;
    private final PracticeMediaBindingPort practiceMediaBindingService;
    private final String authoringFolder;
    private final String submissionFolder;
    private final Clock clock;

    CloudinaryUploadRequester(MediaAssetRepository repository, CloudinaryRequestSigner signer,
            MediaAccessGuard accessGuard, PracticeMediaBindingPort practiceMediaBindingService,
            String authoringFolder, String submissionFolder, Clock clock) {
        this.repository = repository;
        this.signer = signer;
        this.accessGuard = accessGuard;
        this.practiceMediaBindingService = practiceMediaBindingService;
        this.authoringFolder = authoringFolder;
        this.submissionFolder = submissionFolder;
        this.clock = clock;
    }

    CloudinaryUploadResponse requestUpload(CloudinaryUploadRequest request, CurrentUser caller) {
        boolean authoringMedia = isAuthoringAssetKind(request.assetKind());
        boolean studentResponse = MediaConstants.STUDENT_RESPONSE_AUDIO.equals(request.assetKind());
        if (authoringMedia && !isPlatformAuthor(caller)) {
            accessGuard.deny(caller, MediaConstants.ACADEMIC_DRAFT_WRITE_REQUIRED_FOR_QUESTION_MEDIA);
        }
        if (studentResponse && !caller.hasRole(SecurityRoles.STUDENT)) {
            accessGuard.deny(caller, MediaConstants.STUDENT_REQUIRED_FOR_RESPONSE_AUDIO);
        }
        if (!authoringMedia && !studentResponse) {
            throw new UnsupportedContentTypeException();
        }
        // Official exam attempts upload unbound response audio; only a request that
        // claims a practice binding must prove it against the live practice item.
        boolean practiceResponse = studentResponse && hasPracticeBinding(request);
        if (practiceResponse) {
            requirePracticeBinding(request);
            practiceMediaBindingService.assertCanUseResponseAudio(
                    request.practiceSessionId(), request.practiceItemId(), caller);
        }
        validateContentType(request.contentType(), request.assetKind());
        long maxBytes = !studentResponse ? MediaConstants.MAX_AUTHORING_BYTES
                : practiceResponse ? MediaConstants.MAX_PRACTICE_RESPONSE_BYTES
                        : MediaConstants.MAX_SUBMISSION_BYTES;
        if (request.sizeBytes() == null || request.sizeBytes() <= 0
                || request.sizeBytes() > maxBytes) {
            throw new UnsupportedContentTypeException();
        }
        long timestamp = clock.instant().getEpochSecond();
        String resourceType = CloudinaryMediaService.IMAGE_TYPES.contains(request.contentType()) ? "image" : "video";
        UUID mediaPublicId = UUID.randomUUID();
        String publicId = mediaPublicId.toString();
        String folder = studentResponse ? submissionFolder : authoringFolder;
        String cloudinaryPublicId = folder + "/" + publicId;
        // Cloudinary defaults to the public `upload` delivery type. Keep the
        // signed upload small and let public media use its stable secure URL.
        String signature = signer.sign("folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp);

        MediaAsset media = new MediaAsset();
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
        if (practiceResponse) {
            media.setPracticeSessionPublicId(request.practiceSessionId());
            media.setPracticeItemPublicId(request.practiceItemId());
            media.setPurpose(request.purpose());
        }
        repository.save(media);

        String uploadUrl = "https://api.cloudinary.com/v1_1/%s/%s/upload".formatted(signer.cloudName(),
                resourceType);
        return new CloudinaryUploadResponse(media.getPublicId(), cloudinaryPublicId, uploadUrl, signer.apiKey(),
                String.valueOf(timestamp),
                signature, folder, resourceType, CloudinaryMediaService.SIGNATURE_TTL_SECONDS);
    }

    private void validateContentType(String contentType, String assetKind) {
        boolean allowed = MediaConstants.IMAGE_PROMPT.equals(assetKind)
                ? CloudinaryMediaService.IMAGE_TYPES.contains(contentType)
                : (MediaConstants.AUDIO_PROMPT.equals(assetKind)
                        || MediaConstants.STUDENT_RESPONSE_AUDIO.equals(assetKind))
                                && CloudinaryMediaService.AUDIO_TYPES.contains(contentType);
        if (!allowed) {
            throw new UnsupportedContentTypeException();
        }
    }

    private boolean hasPracticeBinding(CloudinaryUploadRequest request) {
        return request.practiceSessionId() != null
                || request.practiceItemId() != null
                || request.purpose() != null;
    }

    private void requirePracticeBinding(CloudinaryUploadRequest request) {
        if (practiceMediaBindingService == null
                || request.practiceSessionId() == null
                || request.practiceItemId() == null
                || !PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE.equals(request.purpose())) {
            throw new UnsupportedContentTypeException();
        }
    }

    private boolean isAuthoringAssetKind(String assetKind) {
        return MediaConstants.IMAGE_PROMPT.equals(assetKind) || MediaConstants.AUDIO_PROMPT.equals(assetKind);
    }

    private boolean isPlatformAuthor(CurrentUser caller) {
        return SecurityPolicy.hasCapability(caller, SecurityCapability.ACADEMIC_DRAFT_WRITE);
    }
}
