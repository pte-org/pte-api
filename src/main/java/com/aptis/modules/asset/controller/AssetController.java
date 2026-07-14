package com.aptis.modules.asset.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.asset.domain.AssetType;
import com.aptis.modules.asset.dto.request.CreateAssetRequest;
import com.aptis.modules.asset.dto.response.AssetResponse;
import com.aptis.modules.asset.interfaces.AssetOperations;
import com.aptis.modules.storage.dto.response.UploadResponse;
import com.aptis.modules.storage.interfaces.StorageService;
import com.aptis.modules.storage.constant.StorageConstants;
import com.aptis.modules.iam.repository.AdminRepository;
import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetOperations assetOperations;
    private final StorageService storageService;
    private final AdminRepository adminRepository;

    public AssetController(AssetOperations assetOperations, StorageService storageService, AdminRepository adminRepository) {
        this.assetOperations = assetOperations;
        this.storageService = storageService;
        this.adminRepository = adminRepository;
    }

    @PreAuthorize("hasAuthority('ADMIN')") // Adjust authority as per your roles
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetResponse>> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "assetType", required = false, defaultValue = "IMAGE_QUESTION") AssetType assetType,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {

        // Determine target folder based on assetType
        String folder = StorageConstants.FOLDER_QUESTIONS;
        if (assetType == AssetType.AUDIO_RECORDING) {
            folder = StorageConstants.FOLDER_ANSWERS;
        }

        // Upload to Cloudinary
        UploadResponse uploadResponse = storageService.uploadFile(file, folder);

        // Resolve user public ID
        UUID uploaderId = resolveUserPublicId(principal.userId());

        // Save DB record
        CreateAssetRequest request = new CreateAssetRequest(
                assetType,
                uploadResponse.storageKey(),
                uploadResponse.cdnUrl(),
                uploadResponse.filename(),
                uploadResponse.sizeBytes(),
                uploadResponse.mimeType(),
                uploaderId,
                null,
                null
        );

        AssetResponse response = assetOperations.createAsset(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED,
                        "ASSET_UPLOAD",
                        "Asset uploaded successfully",
                        response,
                        servletRequest.getRequestURI()));
    }

    private UUID resolveUserPublicId(Long userId) {
        return adminRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                .getPublicId();
    }
}
