package com.pte.scoring.internal.vendor.gemini;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/** Fetches the Describe Image picture: resolves the media id to a presigned URL, downloads it, names its type. */
@Component
public class PromptImageLoader {

    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private final MediaService mediaService;
    private final RestClient mediaDownloadRestClient;
    private final AiProviderProperties properties;

    public PromptImageLoader(MediaService mediaService,
                             @Qualifier("aiMediaDownloadRestClient") RestClient mediaDownloadRestClient,
                             AiProviderProperties properties) {
        this.mediaService = mediaService;
        this.mediaDownloadRestClient = mediaDownloadRestClient;
        this.properties = properties;
    }

    public GeminiImage load(UUID mediaPublicId, UUID tenantId) {
        if (tenantId == null) {
            throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_TENANT_ID_MISSING);
        }
        PresignedDownloadResponse presigned;
        try {
            presigned = mediaService.presignGet(mediaPublicId, properties.getMediaUrlTtlSeconds(), tenantId);
        } catch (RuntimeException ex) {
            throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_RESOLUTION_FAILED, ex);
        }
        if (presigned.url() == null || presigned.url().isBlank()) {
            throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_UNRESOLVED);
        }
        byte[] data;
        try {
            // A presigned URL is already encoded: URI.create keeps it byte for byte, uri(String) would re-encode it.
            data = mediaDownloadRestClient.get().uri(URI.create(presigned.url())).exchange((request, response) -> {
                if (response.getStatusCode().isError()) {
                    throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_DOWNLOAD_FAILED);
                }
                // Read one byte past the cap so an oversize body is never buffered whole.
                return response.getBody().readNBytes(MAX_IMAGE_BYTES + 1);
            });
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientException | IllegalArgumentException ex) {
            throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_DOWNLOAD_FAILED, ex);
        }
        if (data == null || data.length == 0) {
            throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_EMPTY);
        }
        if (data.length > MAX_IMAGE_BYTES) {
            throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_TOO_LARGE);
        }
        return new GeminiImage(mimeType(data), data);
    }

    // The type comes from the file's own bytes: the stored content type is not trusted.
    private static String mimeType(byte[] data) {
        if (startsWith(data, 0x89, 'P', 'N', 'G')) {
            return "image/png";
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F')
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        throw new AiProviderException(ScoringConstants.PROMPT_IMAGE_TYPE_UNSUPPORTED);
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
