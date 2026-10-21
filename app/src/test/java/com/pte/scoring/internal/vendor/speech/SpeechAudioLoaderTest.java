package com.pte.scoring.internal.vendor.speech;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.RecordingHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechAudioLoaderTest {

    private static final UUID MEDIA = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    private MediaService mediaService;
    private RecordingHttpServer storage;
    private SpeechAudioLoader loader;

    @BeforeEach
    void setUp() throws IOException {
        storage = RecordingHttpServer.start();
        loader = new SpeechAudioLoader(mediaService, RestClient.builder().build(), new AiProviderProperties());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void downloadsTheBytesBehindThePresignedUrl() {
        storage.enqueue(200, "audio-bytes");
        presigned(storage.uri("/rec.wav").toString());

        assertThat(loader.load(MEDIA.toString(), TENANT)).isEqualTo("audio-bytes".getBytes());
    }

    @Test
    void rejectsAMediaIdThatIsNotAUuidBeforeAnyLookup() {
        assertThatThrownBy(() -> loader.load("not-a-uuid", TENANT))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_MEDIA_ID_INVALID));
        verifyNoInteractions(mediaService);
    }

    @Test
    void rejectsAMissingTenant() {
        assertThatThrownBy(() -> loader.load(MEDIA.toString(), null))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_TENANT_ID_MISSING));
    }

    @Test
    void aMediaLookupFailureIsAProviderFailureNotARawException() {
        when(mediaService.presignGet(any(), anyLong(), any())).thenThrow(new IllegalStateException("gone"));

        assertThatThrownBy(() -> loader.load(MEDIA.toString(), TENANT))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_MEDIA_RESOLUTION_FAILED));
    }

    @Test
    void aBlankPresignedUrlIsUnresolved() {
        presigned(" ");

        assertThatThrownBy(() -> loader.load(MEDIA.toString(), TENANT))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_MEDIA_UNRESOLVED));
    }

    @Test
    void aFailedDownloadIsAProviderFailure() {
        storage.enqueue(404, "");
        presigned(storage.uri("/rec.wav").toString());

        assertThatThrownBy(() -> loader.load(MEDIA.toString(), TENANT))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_MEDIA_DOWNLOAD_FAILED));
    }

    @Test
    void anEmptyDownloadIsRejected() {
        storage.enqueue(200, "");
        presigned(storage.uri("/rec.wav").toString());

        assertThatThrownBy(() -> loader.load(MEDIA.toString(), TENANT))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_MEDIA_EMPTY));
    }

    private void presigned(String url) {
        when(mediaService.presignGet(any(), anyLong(), any()))
                .thenReturn(new PresignedDownloadResponse(url, 300, null));
    }
}
