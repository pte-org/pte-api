package com.pte.media;

import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.service.PresignService;
import com.pte.media.internal.service.CloudinaryMediaService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The only door other modules use to reach {@code media}. {@code
 * PresignService} and {@code MediaObjectRepository} stay in {@code internal/}.
 *
 * <p>Starts with the one method known to have cross-module callers coming:
 * attempt (Phase 07) resolving a pinned audio prompt's playback URL, and
 * scoring (Phase 08) reading it back for grading. {@code requestUpload} and
 * {@code completeUpload} stay internal — only the owning student's own
 * browser/app calls those, through {@code MediaController} directly.
 */
@Service
public class MediaService {

    private final PresignService presignService;
    private final CloudinaryMediaService cloudinaryMediaService;

    @Autowired
    public MediaService(PresignService presignService, CloudinaryMediaService cloudinaryMediaService) {
        this.presignService = presignService;
        this.cloudinaryMediaService = cloudinaryMediaService;
    }

    /** Compatibility constructor for narrow unit tests that exercise MinIO-only delivery. */
    public MediaService(PresignService presignService) {
        this(presignService, null);
    }

    /** Trusted application call — {@code tenantId} must come from the caller's own verified context, never from request input it merely forwards. */
    public PresignedDownloadResponse presignGet(UUID mediaPublicId, long ttlSeconds, UUID tenantId) {
        if (cloudinaryMediaService != null) {
            PresignedDownloadResponse cloudinary = cloudinaryMediaService.resolveForTrustedCaller(
                    mediaPublicId, ttlSeconds, tenantId);
            if (cloudinary != null) {
                return cloudinary;
            }
        }
        return presignService.presignGet(mediaPublicId, ttlSeconds, tenantId);
    }

    public void validateAuthoringMedia(UUID mediaPublicId, String assetKind, CurrentUser caller) {
        if (cloudinaryMediaService != null) {
            cloudinaryMediaService.validateAuthoringMedia(mediaPublicId, assetKind, caller);
        }
    }
}
