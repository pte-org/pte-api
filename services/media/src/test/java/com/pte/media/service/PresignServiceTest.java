package com.pte.media.service;

import com.pte.common.security.CurrentUser;
import com.pte.media.domain.MediaObject;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.domain.exception.InvalidWavFileException;
import com.pte.media.domain.exception.UnsupportedContentTypeException;
import com.pte.media.dto.request.RequestUploadRequest;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.repository.MediaObjectRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Covers plans/phat-speaking-dynamic-prep-timing Phase 1: WAV-only
 * audio-prompt uploads, WAV-header duration extraction, and the combined
 * presigned-download response now carrying that duration.
 *
 * {@code extractWavDurationSeconds}/{@code parseWavDurationSeconds} are
 * exercised via {@code Mockito.spy} rather than mocking MinIO's own
 * {@code GetObjectResponse} type directly — see those methods' own doc
 * comments on {@link PresignService} for why.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PresignService")
class PresignServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MEDIA_ID = UUID.randomUUID();
    private static final String BUCKET = "pte-media";

    @Mock
    private MediaObjectRepository mediaObjectRepository;

    @Mock
    private MinioClient presignMinioClient;

    @Mock
    private MinioClient minioClient;

    private PresignService service;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        service = new PresignService(mediaObjectRepository, presignMinioClient, minioClient, BUCKET);
        caller = new CurrentUser(USER_ID, TENANT_ID, java.util.List.of());
    }

    // ------------------------------------------------------------------
    // requestUpload — content-type gating
    // ------------------------------------------------------------------

    @Test
    @DisplayName("audioPrompt=true with contentType=audio/wav is accepted and persists audioPrompt=true")
    void requestUpload_audioPromptWav_accepted() throws Exception {
        when(mediaObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/put");

        service.requestUpload(new RequestUploadRequest("audio/wav", true), caller);

        var captor = org.mockito.ArgumentCaptor.forClass(MediaObject.class);
        verify(mediaObjectRepository).save(captor.capture());
        assertThat(captor.getValue().isAudioPrompt()).isTrue();
        assertThat(captor.getValue().getContentType()).isEqualTo("audio/wav");
    }

    @Test
    @DisplayName("audioPrompt=true with a non-WAV contentType (e.g. audio/mpeg) is rejected, even though mpeg is in the general allow-list")
    void requestUpload_audioPromptNonWav_rejected() {
        assertThatThrownBy(() -> service.requestUpload(new RequestUploadRequest("audio/mpeg", true), caller))
                .isInstanceOf(UnsupportedContentTypeException.class);
    }

    @Test
    @DisplayName("audioPrompt absent (null) with audio/mpeg is accepted — the existing general allow-list is untouched")
    void requestUpload_noAudioPromptSignal_generalAllowListUnaffected() throws Exception {
        when(mediaObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/put");

        service.requestUpload(new RequestUploadRequest("audio/mpeg", null), caller);

        var captor = org.mockito.ArgumentCaptor.forClass(MediaObject.class);
        verify(mediaObjectRepository).save(captor.capture());
        assertThat(captor.getValue().isAudioPrompt()).isFalse();
    }

    @Test
    @DisplayName("audioPrompt=false explicitly behaves identically to absent — general allow-list, not narrowed to WAV")
    void requestUpload_audioPromptFalse_generalAllowList() throws Exception {
        when(mediaObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/put");

        service.requestUpload(new RequestUploadRequest("audio/webm", false), caller);

        verify(mediaObjectRepository).save(any());
    }

    @Test
    @DisplayName("image/png is accepted for a Describe Image prompt upload (plans/phat-describe-image-e2e — "
            + "found missing from the general allow-list during that plan's own manual walkthrough)")
    void requestUpload_imagePng_accepted() throws Exception {
        when(mediaObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/put");

        service.requestUpload(new RequestUploadRequest("image/png", null), caller);

        var captor = org.mockito.ArgumentCaptor.forClass(MediaObject.class);
        verify(mediaObjectRepository).save(captor.capture());
        assertThat(captor.getValue().isAudioPrompt()).isFalse();
    }

    @Test
    @DisplayName("image/jpeg is accepted too — same general allow-list, not narrowed to a single image format")
    void requestUpload_imageJpeg_accepted() throws Exception {
        when(mediaObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/put");

        service.requestUpload(new RequestUploadRequest("image/jpeg", null), caller);

        verify(mediaObjectRepository).save(any());
    }

    @Test
    @DisplayName("an unrelated content type (e.g. application/pdf) is still rejected — the allow-list is not wide open")
    void requestUpload_unrelatedContentType_stillRejected() {
        assertThatThrownBy(() -> service.requestUpload(new RequestUploadRequest("application/pdf", null), caller))
                .isInstanceOf(UnsupportedContentTypeException.class);
    }

    // ------------------------------------------------------------------
    // completeUpload — orchestration (extraction stubbed via spy)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("audioPrompt object: duration is extracted, persisted, and the object is marked UPLOADED")
    void completeUpload_audioPrompt_persistsDuration() {
        PresignService spyService = spy(service);
        doReturn(7).when(spyService).extractWavDurationSeconds(anyString());

        MediaObject media = pendingAudioPromptMedia();
        when(mediaObjectRepository.findByPublicIdAndTenantId(MEDIA_ID, TENANT_ID)).thenReturn(Optional.of(media));

        spyService.completeUpload(MEDIA_ID, caller);

        assertThat(media.getDurationSeconds()).isEqualTo(7);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        verify(mediaObjectRepository).save(media);
    }

    @Test
    @DisplayName("audioPrompt object whose duration can't be extracted: fails, never marked UPLOADED, never saved")
    void completeUpload_audioPromptExtractionFails_neverMarkedUploaded() {
        PresignService spyService = spy(service);
        doThrow(new InvalidWavFileException()).when(spyService).extractWavDurationSeconds(anyString());

        MediaObject media = pendingAudioPromptMedia();
        when(mediaObjectRepository.findByPublicIdAndTenantId(MEDIA_ID, TENANT_ID)).thenReturn(Optional.of(media));

        assertThatThrownBy(() -> spyService.completeUpload(MEDIA_ID, caller))
                .isInstanceOf(InvalidWavFileException.class)
                .satisfies(ex -> assertThat(((InvalidWavFileException) ex).getStatus()).isEqualTo(UNPROCESSABLE_ENTITY));

        assertThat(media.getStatus()).isNotEqualTo(MediaStatus.UPLOADED);
        assertThat(media.getDurationSeconds()).isNull();
        verify(mediaObjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("non-audio-prompt object (e.g. a candidate's own recorded answer): extraction is never attempted")
    void completeUpload_nonAudioPrompt_extractionNeverCalled() {
        PresignService spyService = spy(service);

        MediaObject media = new MediaObject();
        media.setTenantId(TENANT_ID);
        media.setOwnerPublicId(USER_ID);
        media.setContentType("audio/wav");
        media.setStorageKey("audio/tenant/answer.wav");
        media.setAudioPrompt(false);
        when(mediaObjectRepository.findByPublicIdAndTenantId(MEDIA_ID, TENANT_ID)).thenReturn(Optional.of(media));

        spyService.completeUpload(MEDIA_ID, caller);

        verify(spyService, never()).extractWavDurationSeconds(anyString());
        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        assertThat(media.getDurationSeconds()).isNull();
    }

    // ------------------------------------------------------------------
    // parseWavDurationSeconds — real WAV bytes, no mocking
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a real, valid WAV stream yields its exact duration")
    void parseWavDurationSeconds_validWav_exactDuration() {
        InputStream wav = generateSilentWav(3);

        int duration = service.parseWavDurationSeconds(wav);

        assertThat(duration).isEqualTo(3);
    }

    @Test
    @DisplayName("garbage bytes (not a WAV file at all) throw InvalidWavFileException")
    void parseWavDurationSeconds_corruptBytes_throws() {
        InputStream garbage = new ByteArrayInputStream("this is not a wav file".getBytes());

        assertThatThrownBy(() -> service.parseWavDurationSeconds(garbage))
                .isInstanceOf(InvalidWavFileException.class);
    }

    @Test
    @DisplayName("an empty stream throws InvalidWavFileException")
    void parseWavDurationSeconds_emptyStream_throws() {
        assertThatThrownBy(() -> service.parseWavDurationSeconds(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(InvalidWavFileException.class);
    }

    // ------------------------------------------------------------------
    // presignGet — duration threaded through the combined response
    // ------------------------------------------------------------------

    @Test
    @DisplayName("presignGet's response carries the media object's stored duration")
    void presignGet_includesDuration() throws Exception {
        MediaObject media = new MediaObject();
        media.setTenantId(TENANT_ID);
        media.setOwnerPublicId(USER_ID);
        media.setContentType("audio/wav");
        media.setStorageKey("audio/tenant/prompt.wav");
        media.setAudioPrompt(true);
        media.markUploaded();
        media.setDurationSeconds(9);
        when(mediaObjectRepository.findByPublicIdAndTenantId(MEDIA_ID, TENANT_ID)).thenReturn(Optional.of(media));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/get");

        PresignedDownloadResponse response = service.presignGet(MEDIA_ID, 3600L, TENANT_ID);

        assertThat(response.durationSeconds()).isEqualTo(9);
    }

    @Test
    @DisplayName("presignGet's response has a null duration for a non-audio-prompt media object")
    void presignGet_nonAudioPrompt_nullDuration() throws Exception {
        MediaObject media = new MediaObject();
        media.setTenantId(TENANT_ID);
        media.setOwnerPublicId(USER_ID);
        media.setContentType("audio/wav");
        media.setStorageKey("audio/tenant/answer.wav");
        media.setAudioPrompt(false);
        media.markUploaded();
        when(mediaObjectRepository.findByPublicIdAndTenantId(MEDIA_ID, TENANT_ID)).thenReturn(Optional.of(media));
        when(presignMinioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://minio.local/get");

        PresignedDownloadResponse response = service.presignGet(MEDIA_ID, 3600L, TENANT_ID);

        assertThat(response.durationSeconds()).isNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MediaObject pendingAudioPromptMedia() {
        MediaObject media = new MediaObject();
        media.setTenantId(TENANT_ID);
        media.setOwnerPublicId(USER_ID);
        media.setContentType("audio/wav");
        media.setStorageKey("audio/tenant/prompt.wav");
        media.setAudioPrompt(true);
        return media;
    }

    /** Generates a real, valid, silent 16-bit mono PCM WAV of exactly [seconds] duration. */
    private InputStream generateSilentWav(int seconds) {
        int sampleRate = 8000;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        byte[] silence = new byte[seconds * sampleRate * 2];
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             AudioInputStream audioInputStream = new AudioInputStream(
                     new ByteArrayInputStream(silence), format, seconds * (long) sampleRate)) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Test fixture generation failed", ex);
        }
    }
}
