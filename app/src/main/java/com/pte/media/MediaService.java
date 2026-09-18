package com.pte.media;

import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.service.CloudinaryMediaService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The only door other modules use to reach {@code media}. The repository stays
 * in {@code internal/}.
 *
 * <p>Starts with the one method known to have cross-module callers coming:
 * attempt (Phase 07) resolving a pinned audio prompt's playback URL, and
 * scoring (Phase 08) reading it back for grading. Upload and completion stay
 * internal — only the owning author's browser calls those through
 * {@code MediaController} directly.
 */
@Service
public class MediaService {

    private final CloudinaryMediaService cloudinaryMediaService;

    @Autowired
    public MediaService(CloudinaryMediaService cloudinaryMediaService) {
        this.cloudinaryMediaService = cloudinaryMediaService;
    }

    /** Trusted application call — {@code tenantId} must come from the caller's own verified context, never from request input it merely forwards. */
    public PresignedDownloadResponse presignGet(UUID mediaPublicId, long ttlSeconds, UUID tenantId) {
        return cloudinaryMediaService.resolveForTrustedCaller(mediaPublicId, ttlSeconds, tenantId);
    }

    public void validateAuthoringMedia(UUID mediaPublicId, String assetKind, CurrentUser caller) {
        cloudinaryMediaService.validateAuthoringMedia(mediaPublicId, assetKind, caller);
    }
}
