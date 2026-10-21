package com.pte.scoring.internal.vendor.gemini;

import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class PromptImageLoaderTest {

    private static final UUID MEDIA = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final String URL = "https://storage.example.test/prompt-image";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 0};

    @Mock
    private MediaService mediaService;
    private MockRestServiceServer storage;
    private PromptImageLoader loader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        storage = MockRestServiceServer.bindTo(builder).build();
        loader = new PromptImageLoader(mediaService, builder.build(), new AiProviderProperties());
    }

    @Test
    void downloadsThePresignedImageAndNamesItsTypeFromTheBytes() {
        presigned(URL);
        storage.expect(requestTo(URL)).andRespond(withSuccess(PNG, org.springframework.http.MediaType.IMAGE_PNG));

        GeminiImage image = loader.load(MEDIA, TENANT);

        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(image.data()).isEqualTo(PNG);
    }

    @Test
    void recognizesJpegAndWebpToo() {
        presigned(URL);
        storage.expect(requestTo(URL)).andRespond(withSuccess(JPEG, org.springframework.http.MediaType.APPLICATION_OCTET_STREAM));
        storage.expect(requestTo(URL)).andRespond(withSuccess(WEBP, org.springframework.http.MediaType.APPLICATION_OCTET_STREAM));

        assertThat(loader.load(MEDIA, TENANT).mimeType()).isEqualTo("image/jpeg");
        assertThat(loader.load(MEDIA, TENANT).mimeType()).isEqualTo("image/webp");
    }

    @Test
    void aFileThatIsNotAnImageIsRejected() {
        presigned(URL);
        storage.expect(requestTo(URL)).andRespond(withSuccess("<html>not an image</html>",
                org.springframework.http.MediaType.TEXT_HTML));

        assertFailure(ScoringConstants.PROMPT_IMAGE_TYPE_UNSUPPORTED);
    }

    @Test
    void anImageOverTheSizeLimitIsRejected() {
        presigned(URL);
        byte[] huge = new byte[PromptImageLoader.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, huge, 0, PNG.length);
        storage.expect(requestTo(URL)).andRespond(withSuccess(huge, org.springframework.http.MediaType.IMAGE_PNG));

        assertFailure(ScoringConstants.PROMPT_IMAGE_TOO_LARGE);
    }

    @Test
    void aPresignedUrlWithEncodedCharactersIsRequestedAsIs() {
        String signed = "https://storage.example.test/a%2Fb.png?X-Sig=ab%2Bcd%3D&X-Date=1";
        presigned(signed);
        storage.expect(requestTo(signed)).andRespond(withSuccess(PNG, org.springframework.http.MediaType.IMAGE_PNG));

        assertThat(loader.load(MEDIA, TENANT).data()).isEqualTo(PNG);
        storage.verify();
    }

    @Test
    void aUrlThatCannotBeParsedIsADownloadFailureNotACrash() {
        presigned("https://storage.example.test/{broken}");

        assertFailure(ScoringConstants.PROMPT_IMAGE_DOWNLOAD_FAILED);
    }

    @Test
    void aMissingTenantIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> loader.load(MEDIA, null))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.PROMPT_IMAGE_TENANT_ID_MISSING));
        verifyNoInteractions(mediaService);
    }

    @Test
    void aMediaLookupFailureIsAProviderFailureNotARawException() {
        when(mediaService.presignGet(any(), anyLong(), any())).thenThrow(new IllegalStateException("other tenant"));

        assertFailure(ScoringConstants.PROMPT_IMAGE_RESOLUTION_FAILED);
    }

    @Test
    void aBlankPresignedUrlIsUnresolved() {
        presigned(" ");

        assertFailure(ScoringConstants.PROMPT_IMAGE_UNRESOLVED);
    }

    @Test
    void aFailedDownloadIsAProviderFailure() {
        presigned(URL);
        storage.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFailure(ScoringConstants.PROMPT_IMAGE_DOWNLOAD_FAILED);
    }

    @Test
    void anEmptyDownloadIsRejected() {
        presigned(URL);
        storage.expect(requestTo(URL)).andRespond(withSuccess(new byte[0],
                org.springframework.http.MediaType.IMAGE_PNG));

        assertFailure(ScoringConstants.PROMPT_IMAGE_EMPTY);
    }

    private void assertFailure(String message) {
        assertThatThrownBy(() -> loader.load(MEDIA, TENANT))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(message));
    }

    private void presigned(String url) {
        when(mediaService.presignGet(any(), anyLong(), any()))
                .thenReturn(new PresignedDownloadResponse(url, 300, null));
    }
}
