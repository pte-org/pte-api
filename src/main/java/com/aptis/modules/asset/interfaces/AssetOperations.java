package com.aptis.modules.asset.interfaces;

import java.util.UUID;

import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.dto.response.AssetResponse;

public interface AssetOperations {
    AssetResponse createAsset(CreateAssetRequest request);
    AssetResponse getAsset(UUID publicId);
    java.util.List<AssetResponse> getAssets(java.util.List<UUID> publicIds);
    void deleteAsset(UUID publicId);
}
