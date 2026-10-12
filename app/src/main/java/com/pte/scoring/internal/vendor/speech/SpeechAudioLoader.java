package com.pte.scoring.internal.vendor.speech;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.UUID;

/** Fetches a candidate's recording: resolves the media id to a presigned URL, then downloads the bytes. */
@Component
public class SpeechAudioLoader {

    private final MediaService mediaService;
    private final RestClient mediaDownloadRestClient;
    private final AiProviderProperties properties;

    public SpeechAudioLoader(MediaService mediaService,
                             @Qualifier("aiMediaDownloadRestClient") RestClient mediaDownloadRestClient,
                             AiProviderProperties properties) {
        this.mediaService = mediaService;
        this.mediaDownloadRestClient = mediaDownloadRestClient;
        this.properties = properties;
    }

    public byte[] load(String audioMediaPublicId, UUID tenantId) {
        UUID mediaPublicId;
        try {
            mediaPublicId = UUID.fromString(audioMediaPublicId);
        } catch (RuntimeException ex) {
            throw new AiProviderException(ScoringConstants.SPEECH_MEDIA_ID_INVALID, ex);
        }
        if (tenantId == null) {
            throw new AiProviderException(ScoringConstants.SPEECH_TENANT_ID_MISSING);
        }
        PresignedDownloadResponse presigned;
        try {
            presigned = mediaService.presignGet(mediaPublicId, properties.getMediaUrlTtlSeconds(), tenantId);
        } catch (RuntimeException ex) {
            throw new AiProviderException(ScoringConstants.SPEECH_MEDIA_RESOLUTION_FAILED, ex);
        }
        if (presigned.url() == null || presigned.url().isBlank()) {
            throw new AiProviderException(ScoringConstants.SPEECH_MEDIA_UNRESOLVED);
        }
        byte[] audio;
        try {
            audio = mediaDownloadRestClient.get().uri(URI.create(presigned.url())).retrieve().body(byte[].class);
        } catch (RestClientException ex) {
            throw new AiProviderException(ScoringConstants.SPEECH_MEDIA_DOWNLOAD_FAILED, ex);
        }
        if (audio == null || audio.length == 0) {
            throw new AiProviderException(ScoringConstants.SPEECH_MEDIA_EMPTY);
        }
        return audio;
    }
}
