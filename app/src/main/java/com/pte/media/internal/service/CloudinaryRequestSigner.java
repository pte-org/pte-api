package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.exception.UnsupportedContentTypeException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Holds the Cloudinary credentials: signs upload/notification payloads and builds signed private reads. */
class CloudinaryRequestSigner {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final Clock clock;

    CloudinaryRequestSigner(String cloudName, String apiKey, String apiSecret, Clock clock) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.clock = clock;
    }

    String cloudName() {
        return cloudName;
    }

    String apiKey() {
        return apiKey;
    }

    String privateDownloadUrl(MediaAsset media, long ttlSeconds) {
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> signedParameters = new TreeMap<>();
        signedParameters.put("attachment", "false");
        signedParameters.put("expires_at", String.valueOf(timestamp + ttlSeconds));
        signedParameters.put("format", fileFormat(media.getContentType()));
        signedParameters.put("public_id", media.getCloudinaryPublicId());
        signedParameters.put("timestamp", String.valueOf(timestamp));
        signedParameters.put("type", CloudinaryDeliveryType.AUTHENTICATED.name().toLowerCase(java.util.Locale.ROOT));
        String payload = signedParameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        String signature = sign(payload);
        String query = signedParameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return "https://api.cloudinary.com/v1_1/%s/%s/download?%s&signature=%s&api_key=%s".formatted(
                cloudName, media.getCloudinaryResourceType(), query, signature, encode(apiKey));
    }

    private String fileFormat(String contentType) {
        return switch (contentType) {
            case "audio/wav" -> "wav";
            case "audio/mpeg" -> "mp3";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new UnsupportedContentTypeException();
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    String sign(String payload) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException(MediaConstants.CLOUDINARY_CREDENTIALS_NOT_CONFIGURED);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest((payload + apiSecret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(MediaConstants.SHA1_UNAVAILABLE, ex);
        }
    }
}
