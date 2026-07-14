package com.aptis.modules.asset.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.asset.domain.Asset;
import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.asset.repository.AssetRepository;

@Service
@Transactional(readOnly = true)
public class AssetService implements AssetOperations {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    @Transactional
    public AssetResponse createAsset(CreateAssetRequest request) {
        Asset asset = Asset.create(
            request.assetType(),
            request.storageKey(),
            request.cdnUrl(),
            request.filename(),
            request.sizeBytes(),
            request.mimeType(),
            request.uploadedBy(),
            request.tenantId(),
            request.expiresAt()
        );
        Asset savedAsset = assetRepository.save(asset);
        return AssetResponse.from(savedAsset);
    }

    @Override
    public AssetResponse getAsset(UUID publicId) {
        Asset asset = assetRepository.findByPublicIdAndDeletedFalse(publicId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return AssetResponse.from(asset);
    }

    @Override
    public java.util.List<AssetResponse> getAssets(java.util.List<UUID> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Asset> assets = assetRepository.findByPublicIdIn(publicIds);
        return assets.stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .map(AssetResponse::from)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAsset(UUID publicId) {
        Asset asset = assetRepository.findByPublicIdAndDeletedFalse(publicId)
            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        asset.setDeleted(true);
        assetRepository.save(asset);
    }
}
