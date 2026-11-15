package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.internal.constant.MediaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/** Deletes one stored file at Cloudinary with a signed destroy call. */
class CloudinaryAssetDestroyer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudinaryAssetDestroyer.class);

    private final CloudinaryRequestSigner signer;
    private final RestClient restClient;
    private final Clock clock;

    CloudinaryAssetDestroyer(CloudinaryRequestSigner signer, RestClient.Builder restClientBuilder, Clock clock) {
        this.signer = signer;
        this.restClient = restClientBuilder.build();
        this.clock = clock;
    }

    /**
     * True when the file is gone at Cloudinary, including when it was already missing; false when Cloudinary could not
     * be reached or refused, so the caller keeps its row and can try again later.
     */
    boolean destroy(MediaAsset asset) {
        String deliveryType = asset.getCloudinaryDeliveryType().name().toLowerCase(Locale.ROOT);
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        // Cloudinary signs the parameters in alphabetical order.
        String signature = signer.sign("invalidate=true&public_id=" + asset.getCloudinaryPublicId()
                + "&timestamp=" + timestamp + "&type=" + deliveryType);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("public_id", asset.getCloudinaryPublicId());
        form.add("type", deliveryType);
        form.add("invalidate", "true");
        form.add("timestamp", timestamp);
        form.add("api_key", signer.apiKey());
        form.add("signature", signature);
        try {
            Map<?, ?> body = restClient.post()
                    .uri("https://api.cloudinary.com/v1_1/%s/%s/destroy".formatted(
                            signer.cloudName(), asset.getCloudinaryResourceType()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object result = body == null ? null : body.get("result");
            boolean gone = MediaConstants.CLOUDINARY_DESTROY_RESULT_OK.equals(result)
                    || MediaConstants.CLOUDINARY_DESTROY_RESULT_NOT_FOUND.equals(result);
            if (!gone) {
                LOGGER.warn("Cloudinary destroy did not remove media {}: result={}", asset.getPublicId(), result);
            }
            return gone;
        } catch (RestClientException ex) {
            LOGGER.warn("Cloudinary destroy failed for media {}: {}", asset.getPublicId(),
                    ex.getClass().getSimpleName());
            return false;
        }
    }
}
