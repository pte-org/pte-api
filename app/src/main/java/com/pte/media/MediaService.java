package com.pte.media;

import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.service.PresignService;
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

    public MediaService(PresignService presignService) {
        this.presignService = presignService;
    }

    /** Trusted application call — {@code tenantId} must come from the caller's own verified context, never from request input it merely forwards. */
    public PresignedDownloadResponse presignGet(UUID mediaPublicId, long ttlSeconds, UUID tenantId) {
        return presignService.presignGet(mediaPublicId, ttlSeconds, tenantId);
    }
}
