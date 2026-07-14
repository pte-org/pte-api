package com.aptis.modules.asset.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

import com.aptis.modules.asset.domain.AssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(
    @NotNull(message = "Asset type is required")
    AssetType assetType,

    @NotBlank(message = "Storage key is required")
    @Size(max = 1024, message = "Storage key cannot exceed 1024 characters")
    String storageKey,

    @Size(max = 1024, message = "CDN URL cannot exceed 1024 characters")
    String cdnUrl,

    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename cannot exceed 255 characters")
    String filename,

    @NotNull(message = "Size is required")
    Long sizeBytes,

    @NotBlank(message = "MIME type is required")
    @Size(max = 128, message = "MIME type cannot exceed 128 characters")
    String mimeType,

    UUID uploadedBy,
    UUID tenantId,
    LocalDateTime expiresAt
) {}
