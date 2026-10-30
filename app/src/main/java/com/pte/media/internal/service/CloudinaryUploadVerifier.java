package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.dto.request.CloudinaryCompleteRequest;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.exception.MediaNotYetUploadedException;
import com.pte.media.internal.exception.UnsupportedContentTypeException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Verifies Cloudinary's signed completion and checks that an uploaded asset may be used where it is referenced. */
class CloudinaryUploadVerifier {

    private final MediaAssetRepository repository;
    private final CloudinaryRequestSigner signer;
    private final MediaAccessGuard accessGuard;

    CloudinaryUploadVerifier(MediaAssetRepository repository, CloudinaryRequestSigner signer,
            MediaAccessGuard accessGuard) {
        this.repository = repository;
        this.signer = signer;
        this.accessGuard = accessGuard;
    }

    void completeUpload(UUID mediaPublicId, CloudinaryCompleteRequest request, CurrentUser caller) {
        MediaAsset media = repository.findByPublicId(mediaPublicId)
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
                        signer.sign("public_id=" + request.publicId() + "&version=" + request.version())
                                .getBytes(StandardCharsets.US_ASCII),
                        request.signature().getBytes(StandardCharsets.US_ASCII))) {
            throw new MediaNotFoundException();
        }
        if (request.bytes() == null || request.bytes() <= 0 || request.bytes() > media.getSizeBytes()) {
            throw new MediaNotFoundException();
        }
        if (MediaAccessGuard.isPracticeResponseAudio(media)
                && (request.durationSeconds() == null
                        || request.durationSeconds() <= 0
                        || request.durationSeconds() > PracticeMediaConstants.MAX_RESPONSE_DURATION_SECONDS)) {
            throw new MediaNotFoundException();
        }
        if (MediaAccessGuard.isPracticeResponseAudio(media)) {
            accessGuard.requirePracticeBinding(media, caller);
        }
        media.setAssetId(request.assetId());
        media.setSecureUrl(request.secureUrl());
        media.setSizeBytes(request.bytes());
        media.setDurationSeconds(request.durationSeconds());
        media.markUploaded();
    }

    void validateAuthoringMedia(UUID mediaPublicId, String assetKind, CurrentUser caller) {
        MediaAsset media = repository.findByPublicId(mediaPublicId).orElseThrow(MediaNotFoundException::new);
        if (media.getCloudinaryPublicId() == null || media.getStatus() != MediaStatus.UPLOADED) {
            throw new MediaNotYetUploadedException();
        }
        boolean audio = MediaConstants.AUDIO_PROMPT.equals(assetKind);
        boolean contentTypeAllowed = audio ? CloudinaryMediaService.AUDIO_TYPES.contains(media.getContentType())
                : MediaConstants.IMAGE_PROMPT.equals(assetKind)
                        && CloudinaryMediaService.IMAGE_TYPES.contains(media.getContentType());
        if (!contentTypeAllowed || media.isAudioPrompt() != audio) {
            throw new UnsupportedContentTypeException();
        }
        if (!caller.hasRole(SecurityRoles.PLATFORM_ADMIN) && !media.getOwnerPublicId().equals(caller.userId())) {
            accessGuard.deny(caller, MediaConstants.MEDIA_ASSET_OWNED_BY_ANOTHER_AUTHOR);
        }
    }

    void validatePracticeResponseAudio(UUID mediaPublicId, UUID sessionPublicId,
            UUID itemPublicId, CurrentUser caller) {
        if (mediaPublicId == null || sessionPublicId == null || itemPublicId == null || caller == null
                || caller.userId() == null) {
            throw new MediaNotFoundException();
        }
        MediaAsset media = repository.findByPublicId(mediaPublicId)
                .filter(candidate -> candidate.getOwnerPublicId().equals(caller.userId()))
                .orElseThrow(MediaNotFoundException::new);
        if (!MediaAccessGuard.isPracticeResponseAudio(media)
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
        accessGuard.requirePracticeBinding(media, caller);
    }
}
