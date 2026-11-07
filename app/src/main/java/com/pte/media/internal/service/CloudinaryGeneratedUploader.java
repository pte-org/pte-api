package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.exception.UnsupportedContentTypeException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Uploads generated media (audio or image) directly to Cloudinary via server.
 * Builds the MediaAsset in completed state and persists it.
 */
class CloudinaryGeneratedUploader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryGeneratedUploader.class);
    private static final long REQUEST_TIMEOUT_MS = 60_000;

    private final MediaAssetRepository repository;
    private final CloudinaryRequestSigner signer;
    private final String authoringFolder;
    private final Clock clock;
    private final RestClient restClient;

    CloudinaryGeneratedUploader(
            MediaAssetRepository repository,
            CloudinaryRequestSigner signer,
            String authoringFolder,
            Clock clock,
            RestClient.Builder restClientBuilder) {
        this.repository = repository;
        this.signer = signer;
        this.authoringFolder = authoringFolder;
        this.clock = clock;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Upload media bytes to Cloudinary and create a completed MediaAsset.
     *
     * @param bytes media content
     * @param contentType MIME type
     * @param assetKind "AUDIO_PROMPT" or "IMAGE_PROMPT"
     * @param owner current user
     * @return UUID of created media asset
     * @throws UnsupportedContentTypeException on invalid content type or size
     */
    UUID storeGenerated(byte[] bytes, String contentType, String assetKind, CurrentUser owner) {
        validateInput(bytes, contentType, assetKind);

        UUID mediaPublicId = UUID.randomUUID();
        String publicId = mediaPublicId.toString();
        String cloudinaryPublicId = authoringFolder + "/" + publicId;
        String resourceType = isImageType(contentType) ? "image" : "video";

        try {
            // Upload to Cloudinary
            CloudinaryUploadResponse uploadResponse = uploadToCloudinary(
                    bytes, contentType, cloudinaryPublicId, resourceType);

            // Create and persist completed asset
            MediaAsset asset = new MediaAsset();
            asset.setPublicId(mediaPublicId);
            asset.setTenantId(owner.tenantId());
            asset.setOwnerPublicId(owner.userId());
            asset.setContentType(contentType);
            asset.setAudioPrompt(MediaConstants.AUDIO_PROMPT.equals(assetKind));
            asset.setStorageKey(cloudinaryPublicId);
            asset.setCloudinaryPublicId(uploadResponse.publicId);
            asset.setCloudinaryResourceType(resourceType);
            asset.setCloudinaryDeliveryType(CloudinaryDeliveryType.AUTHENTICATED);
            asset.setSecureUrl(uploadResponse.secureUrl);
            asset.setAssetId(uploadResponse.publicId);
            asset.setSizeBytes((long) bytes.length);
            asset.setDurationSeconds(uploadResponse.duration);
            asset.markUploaded();

            repository.save(asset);
            return mediaPublicId;
        } catch (UnsupportedContentTypeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to upload generated media to Cloudinary: errorType={}", e.getClass().getSimpleName());
            throw new UnsupportedContentTypeException();
        }
    }

    /**
     * Removes an asset this user generated: first the file at Cloudinary (a file that is already gone counts as
     * removed), then the row. When Cloudinary cannot be reached the row stays, so nothing is left unreferenced and
     * untracked. Only the user who generated the asset may remove it.
     */
    void deleteGenerated(UUID mediaPublicId, CurrentUser owner) {
        MediaAsset asset = repository.findByPublicId(mediaPublicId).orElse(null);
        if (asset == null) {
            return;
        }
        if (owner == null || !java.util.Objects.equals(owner.userId(), asset.getOwnerPublicId())) {
            throw new IllegalStateException("Only the user who generated the media can remove it");
        }
        String deliveryType = CloudinaryDeliveryType.AUTHENTICATED.name().toLowerCase(java.util.Locale.ROOT);
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String signature = signer.sign("public_id=" + asset.getCloudinaryPublicId() + "&timestamp=" + timestamp
                + "&type=" + deliveryType);
        org.springframework.util.LinkedMultiValueMap<String, String> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.add("public_id", asset.getCloudinaryPublicId());
        form.add("type", deliveryType);
        form.add("timestamp", timestamp);
        form.add("api_key", signer.apiKey());
        form.add("signature", signature);
        restClient.post()
                .uri("https://api.cloudinary.com/v1_1/%s/%s/destroy".formatted(
                        signer.cloudName(), asset.getCloudinaryResourceType()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                    LOGGER.warn("Cloudinary destroy failed: status={}", httpResponse.getStatusCode().value());
                    throw new IllegalStateException("Cloudinary destroy failed");
                })
                .toBodilessEntity();
        repository.delete(asset);
    }

    private void validateInput(byte[] bytes, String contentType, String assetKind) {
        if (bytes == null || bytes.length == 0) {
            throw new UnsupportedContentTypeException();
        }
        if (bytes.length > MediaConstants.MAX_AUTHORING_BYTES) {
            throw new UnsupportedContentTypeException();
        }
        if (!isValidContentType(contentType, assetKind)) {
            throw new UnsupportedContentTypeException();
        }
    }

    private boolean isValidContentType(String contentType, String assetKind) {
        boolean isImage = MediaConstants.IMAGE_PROMPT.equals(assetKind);
        boolean isAudio = MediaConstants.AUDIO_PROMPT.equals(assetKind);

        if (isImage) {
            return CloudinaryMediaService.IMAGE_TYPES.contains(contentType);
        } else if (isAudio) {
            return CloudinaryMediaService.AUDIO_TYPES.contains(contentType);
        }
        return false;
    }

    private boolean isImageType(String contentType) {
        return CloudinaryMediaService.IMAGE_TYPES.contains(contentType);
    }

    /**
     * Signed upload: the signature covers the sorted parameters (public_id, timestamp, type) plus the API secret;
     * the file travels as a data URI. {@code type=authenticated} matches how browser uploads are stored, so the
     * existing signed download URLs can serve these assets.
     */
    private CloudinaryUploadResponse uploadToCloudinary(
            byte[] bytes, String contentType, String cloudinaryPublicId, String resourceType) {
        String url = "https://api.cloudinary.com/v1_1/%s/%s/upload".formatted(signer.cloudName(), resourceType);
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String deliveryType = CloudinaryDeliveryType.AUTHENTICATED.name().toLowerCase(java.util.Locale.ROOT);
        String signature = signer.sign("public_id=" + cloudinaryPublicId + "&timestamp=" + timestamp
                + "&type=" + deliveryType);

        org.springframework.util.LinkedMultiValueMap<String, String> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes));
        form.add("public_id", cloudinaryPublicId);
        form.add("type", deliveryType);
        form.add("timestamp", timestamp);
        form.add("api_key", signer.apiKey());
        form.add("signature", signature);

        Map<String, Object> response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                    LOGGER.warn("Cloudinary upload failed: status={}", httpResponse.getStatusCode().value());
                    throw new IllegalStateException("Cloudinary upload failed");
                })
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { });
        if (response == null || response.get("public_id") == null) {
            throw new IllegalStateException("Invalid Cloudinary response");
        }
        Object duration = response.get("duration");
        return new CloudinaryUploadResponse(
                String.valueOf(response.get("public_id")),
                response.get("secure_url") == null ? null : String.valueOf(response.get("secure_url")),
                duration instanceof Number number ? (int) Math.round(number.doubleValue()) : null);
    }

    record CloudinaryUploadResponse(String publicId, String secureUrl, Integer duration) {}
}
