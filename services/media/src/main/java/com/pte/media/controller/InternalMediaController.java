package com.pte.media.controller;

import com.pte.common.web.ApiResponse;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.service.PresignService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (ROLE_INTERNAL_SERVICE). Added for exam-delivery's
 * StartAttempt-time audio URL resolution (listening-exam-policy Phase 6) — the
 * caller passes the TTL it needs (derived from its own session window), capped
 * server-side at {@code PresignService.MAX_DOWNLOAD_URL_TTL_SECONDS}.
 */
@RestController
@RequestMapping("/internal/media-objects")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalMediaController {

    private final PresignService presignService;

    public InternalMediaController(PresignService presignService) {
        this.presignService = presignService;
    }

    @GetMapping("/{publicId}/presigned-url")
    public ApiResponse<PresignedDownloadResponse> presignGet(@PathVariable UUID publicId,
                                                              @RequestParam long ttlSeconds,
                                                              @RequestParam UUID tenantId) {
        return ApiResponse.success(presignService.presignGet(publicId, ttlSeconds, tenantId));
    }
}
