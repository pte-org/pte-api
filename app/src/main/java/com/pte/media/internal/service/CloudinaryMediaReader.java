package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.dto.response.MediaPreviewResponse;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.exception.MediaNotYetUploadedException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.security.CurrentUser;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Read URLs for uploaded media: owner previews and tenant-scoped resolution for trusted modules. */
class CloudinaryMediaReader {

    private final MediaAssetRepository repository;
    private final CloudinaryRequestSigner signer;

    CloudinaryMediaReader(MediaAssetRepository repository, CloudinaryRequestSigner signer) {
        this.repository = repository;
        this.signer = signer;
    }

    MediaPreviewResponse preview(UUID mediaPublicId, CurrentUser caller) {
        MediaAsset media = repository.findByPublicId(mediaPublicId)
                .filter(candidate -> candidate.getOwnerPublicId().equals(caller.userId()) || caller.hasRole("PLATFORM_ADMIN"))
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() != MediaStatus.UPLOADED || media.getSecureUrl() == null) {
            throw new MediaNotYetUploadedException();
        }
        long ttl = media.getCloudinaryDeliveryType() == CloudinaryDeliveryType.AUTHENTICATED
                ? CloudinaryMediaService.SIGNATURE_TTL_SECONDS : 0;
        String url = ttl == 0 ? media.getSecureUrl() : signer.privateDownloadUrl(media, ttl);
        return new MediaPreviewResponse(url, ttl, media.getDurationSeconds());
    }

    Map<UUID, PresignedDownloadResponse> resolveForTrustedCallers(
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

        Map<UUID, MediaAsset> mediaByPublicId = new HashMap<>();
        repository.findAllForTrustedTenant(requestedIds, tenantId)
                .forEach(media -> mediaByPublicId.put(media.getPublicId(), media));
        Map<UUID, PresignedDownloadResponse> resolved = new HashMap<>();
        for (UUID mediaPublicId : requestedIds) {
            MediaAsset media = mediaByPublicId.get(mediaPublicId);
            if (media == null) {
                throw new MediaNotFoundException();
            }
            resolved.put(mediaPublicId, toTrustedDownloadResponse(media, requestedTtlSeconds, tenantId));
        }
        return Map.copyOf(resolved);
    }

    private PresignedDownloadResponse toTrustedDownloadResponse(
            MediaAsset media, long requestedTtlSeconds, UUID tenantId) {
        if (media.getCloudinaryPublicId() == null) {
            throw new MediaNotFoundException();
        }
        if (media.getTenantId() != null && !media.getTenantId().equals(tenantId)) {
            throw new MediaNotFoundException();
        }
        if (media.getStatus() != MediaStatus.UPLOADED || media.getSecureUrl() == null) {
            throw new MediaNotYetUploadedException();
        }
        long ttl = Math.min(Math.max(requestedTtlSeconds, 1), CloudinaryMediaService.SIGNATURE_TTL_SECONDS);
        if (media.getCloudinaryDeliveryType() != CloudinaryDeliveryType.AUTHENTICATED) {
            // Existing pre-migration assets are still public Cloudinary uploads; never claim their URL expires.
            return new PresignedDownloadResponse(media.getSecureUrl(), 0, media.getDurationSeconds());
        }
        return new PresignedDownloadResponse(signer.privateDownloadUrl(media, ttl), ttl, media.getDurationSeconds());
    }
}
