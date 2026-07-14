package com.aptis.modules.asset.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.common.domain.BaseEntity;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Asset extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    @Column(name = "cdn_url", length = 1024)
    private String cdnUrl;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public static Asset create(
            AssetType assetType,
            String storageKey,
            String cdnUrl,
            String filename,
            Long sizeBytes,
            String mimeType,
            UUID uploadedBy,
            UUID tenantId,
            LocalDateTime expiresAt) {
        Asset asset = new Asset();
        asset.setAssetType(assetType);
        asset.setStorageKey(storageKey);
        asset.setCdnUrl(cdnUrl);
        asset.setFilename(filename);
        asset.setSizeBytes(sizeBytes);
        asset.setMimeType(mimeType);
        asset.setUploadedBy(uploadedBy);
        asset.setTenantId(tenantId);
        asset.setExpiresAt(expiresAt);
        return asset;
    }
}
