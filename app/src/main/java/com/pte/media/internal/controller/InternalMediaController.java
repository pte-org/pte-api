package com.pte.media.internal.controller;

import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.MediaService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compatibility adapter for external callers still on HTTP during dual-run
 * (a service still in {@code services/} until Phase 11 cutover). Code inside
 * {@code app} must call {@link com.pte.media.MediaService} directly, never
 * this controller — it exists only so a not-yet-ported service can keep
 * calling media the same way it always has.
 */
@RestController
@RequestMapping("/internal/media-objects")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalMediaController {

    private final MediaService mediaService;

    public InternalMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/{publicId}/presigned-url")
    public ApiResponse<PresignedDownloadResponse> presignGet(@PathVariable UUID publicId,
                                                              @RequestParam long ttlSeconds,
                                                              @RequestParam UUID tenantId) {
        return ApiResponse.success(mediaService.presignGet(publicId, ttlSeconds, tenantId));
    }
}
