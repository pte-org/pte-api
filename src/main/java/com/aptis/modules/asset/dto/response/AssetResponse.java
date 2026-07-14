package com.aptis.modules.asset.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.aptis.modules.asset.domain.Asset;
import com.aptis.modules.asset.domain.AssetType;

public record AssetResponse(
    UUID publicId,
    AssetType assetType,
    String storageKey,
    String cdnUrl,
    String filename,
    Long sizeBytes,
    String mimeType,
    UUID uploadedBy,
    UUID tenantId,
    LocalDateTime expiresAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
            asset.getPublicId(),
            asset.getAssetType(),
            asset.getStorageKey(),
            asset.getCdnUrl(),
            asset.getFilename(),
            asset.getSizeBytes(),
            asset.getMimeType(),
            asset.getUploadedBy(),
            asset.getTenantId(),
            asset.getExpiresAt(),
            asset.getCreatedAt(),
            asset.getUpdatedAt()
        );
    }
}
