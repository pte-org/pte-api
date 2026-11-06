package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.dto.request.CloudinaryCompleteRequest;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.exception.MediaNotYetUploadedException;
import com.pte.media.internal.exception.UnsupportedContentTypeException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.practice.PracticeMediaConstants;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Signed completion and usage checks of uploaded Cloudinary assets. */
@ExtendWith(MockitoExtension.class)
class CloudinaryUploadVerifierTest {

    private static final String SECRET = "unit-test-secret";
    private static final String PUBLIC_ID = "pte/audio/abc";

    @Mock
    private MediaAssetRepository repository;
    @Mock
    private PracticeMediaBindingPort bindingPort;

    private CloudinaryUploadVerifier verifier;
    private CurrentUser owner;

    @BeforeEach
    void setUp() {
        CloudinaryRequestSigner signer = new CloudinaryRequestSigner("cloud", "key", SECRET, Clock.systemUTC());
        verifier = new CloudinaryUploadVerifier(repository, signer, new MediaAccessGuard(bindingPort, null));
        owner = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    }

    private static String signature(String publicId, long version) {
        try {
            String payload = "public_id=" + publicId + "&version=" + version + SECRET;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MediaAsset pending(String purpose) {
        MediaAsset media = new MediaAsset();
        media.setPublicId(UUID.randomUUID());
        media.setOwnerPublicId(owner.userId());
        media.setContentType(MediaConstants.AUDIO_WAV);
        media.setCloudinaryPublicId(PUBLIC_ID);
        media.setCloudinaryResourceType("video");
        media.setSizeBytes(5_000L);
        media.setPurpose(purpose);
        media.setAudioPrompt(true);
        return media;
    }

    private CloudinaryCompleteRequest request(String publicId, String url, Long bytes, Integer duration,
            Long version, String signature) {
        return new CloudinaryCompleteRequest(publicId, "asset-1", url, "video", "wav", bytes, duration, version,
                signature);
    }

    private CloudinaryCompleteRequest validRequest() {
        return request(PUBLIC_ID, "https://res.cloudinary.com/cloud/video/upload/v7/a.wav", 4_000L, 30, 7L,
                signature(PUBLIC_ID, 7));
    }

    private void found(MediaAsset media) {
        when(repository.findByPublicId(media.getPublicId())).thenReturn(Optional.of(media));
    }

    @Test
    void completeUpload_unknownMedia_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.completeUpload(id, validRequest(), owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_mediaOwnedBySomeoneElse_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        CurrentUser stranger = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), validRequest(), stranger))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_alreadyUploaded_returnsWithoutChangingTheAsset() {
        MediaAsset media = pending(null);
        media.markUploaded();
        found(media);

        verifier.completeUpload(media.getPublicId(), request("other", "http://evil", 1L, null, null, null), owner);

        assertThat(media.getSecureUrl()).isNull();
        assertThat(media.getSizeBytes()).isEqualTo(5_000L);
    }

    @Test
    void completeUpload_publicIdDiffersFromTheIssuedOne_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        CloudinaryCompleteRequest request = request("pte/audio/other",
                "https://res.cloudinary.com/cloud/a.wav", 4_000L, 30, 7L, signature("pte/audio/other", 7));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), request, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_urlOutsideCloudinary_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://evil.example/a.wav", 4_000L, 30, 7L,
                signature(PUBLIC_ID, 7));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), request, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_wrongSignature_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://res.cloudinary.com/cloud/a.wav", 4_000L,
                30, 7L, signature(PUBLIC_ID, 8));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), request, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_missingSignatureOrVersion_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        String url = "https://res.cloudinary.com/cloud/a.wav";

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(),
                request(PUBLIC_ID, url, 4_000L, 30, 7L, null), owner)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(),
                request(PUBLIC_ID, url, 4_000L, 30, null, signature(PUBLIC_ID, 7)), owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_zeroOrMissingBytes_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        String url = "https://res.cloudinary.com/cloud/a.wav";

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(),
                request(PUBLIC_ID, url, 0L, 30, 7L, signature(PUBLIC_ID, 7)), owner))
                .isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(),
                request(PUBLIC_ID, url, null, 30, 7L, signature(PUBLIC_ID, 7)), owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_bytesLargerThanTheReservedSize_isNotFound() {
        MediaAsset media = pending(null);
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://res.cloudinary.com/cloud/a.wav", 5_001L,
                30, 7L, signature(PUBLIC_ID, 7));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), request, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_bytesEqualToTheReservedSize_isAccepted() {
        MediaAsset media = pending(null);
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://res.cloudinary.com/cloud/a.wav", 5_000L,
                30, 7L, signature(PUBLIC_ID, 7));

        verifier.completeUpload(media.getPublicId(), request, owner);

        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    @Test
    void completeUpload_validRequest_storesProviderFieldsAndMarksUploaded() {
        MediaAsset media = pending(null);
        found(media);

        verifier.completeUpload(media.getPublicId(), validRequest(), owner);

        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        assertThat(media.getAssetId()).isEqualTo("asset-1");
        assertThat(media.getSecureUrl()).startsWith("https://res.cloudinary.com/");
        assertThat(media.getSizeBytes()).isEqualTo(4_000L);
        assertThat(media.getDurationSeconds()).isEqualTo(30);
        verifyNoInteractions(bindingPort);
    }

    @Test
    void completeUpload_practiceRecordingWithoutDuration_isNotFound() {
        MediaAsset media = pending(PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE);
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://res.cloudinary.com/cloud/a.wav", 4_000L,
                null, 7L, signature(PUBLIC_ID, 7));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), request, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_practiceRecordingLongerThanTheLimit_isNotFound() {
        MediaAsset media = pending(PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE);
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://res.cloudinary.com/cloud/a.wav", 4_000L,
                PracticeMediaConstants.MAX_RESPONSE_DURATION_SECONDS + 1, 7L, signature(PUBLIC_ID, 7));

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), request, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void completeUpload_practiceRecordingWithoutSessionBinding_isNotFound() {
        MediaAsset media = pending(PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE);
        found(media);

        assertThatThrownBy(() -> verifier.completeUpload(media.getPublicId(), validRequest(), owner))
                .isInstanceOf(MediaNotFoundException.class);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
    }

    @Test
    void completeUpload_practiceRecordingAtDurationLimit_checksTheSessionBindingAndCompletes() {
        MediaAsset media = pending(PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE);
        media.setPracticeSessionPublicId(UUID.randomUUID());
        media.setPracticeItemPublicId(UUID.randomUUID());
        found(media);
        CloudinaryCompleteRequest request = request(PUBLIC_ID, "https://res.cloudinary.com/cloud/a.wav", 4_000L,
                PracticeMediaConstants.MAX_RESPONSE_DURATION_SECONDS, 7L, signature(PUBLIC_ID, 7));

        verifier.completeUpload(media.getPublicId(), request, owner);

        verify(bindingPort).assertCanUseResponseAudio(media.getPracticeSessionPublicId(),
                media.getPracticeItemPublicId(), owner);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    @Test
    void validateAuthoringMedia_unknownMedia_isNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByPublicId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.validateAuthoringMedia(id, MediaConstants.AUDIO_PROMPT, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void validateAuthoringMedia_assetStillPending_isNotYetUploaded() {
        MediaAsset media = pending(null);
        found(media);

        assertThatThrownBy(() -> verifier.validateAuthoringMedia(media.getPublicId(), MediaConstants.AUDIO_PROMPT,
                owner)).isInstanceOf(MediaNotYetUploadedException.class);
    }

    @Test
    void validateAuthoringMedia_audioAssetUsedAsImage_isUnsupported() {
        MediaAsset media = pending(null);
        media.markUploaded();
        found(media);

        assertThatThrownBy(() -> verifier.validateAuthoringMedia(media.getPublicId(), MediaConstants.IMAGE_PROMPT,
                owner)).isInstanceOf(UnsupportedContentTypeException.class);
    }

    @Test
    void validateAuthoringMedia_unknownAssetKind_isUnsupported() {
        MediaAsset media = pending(null);
        media.markUploaded();
        found(media);

        assertThatThrownBy(() -> verifier.validateAuthoringMedia(media.getPublicId(), "VIDEO_PROMPT", owner))
                .isInstanceOf(UnsupportedContentTypeException.class);
    }

    @Test
    void validateAuthoringMedia_assetOfAnotherAuthor_isDenied() {
        MediaAsset media = pending(null);
        media.markUploaded();
        found(media);
        CurrentUser other = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));

        assertThatThrownBy(() -> verifier.validateAuthoringMedia(media.getPublicId(), MediaConstants.AUDIO_PROMPT,
                other)).isInstanceOf(AccessDeniedException.class)
                .hasMessage(MediaConstants.MEDIA_ASSET_OWNED_BY_ANOTHER_AUTHOR);
    }

    @Test
    void validateAuthoringMedia_platformAdminMayUseAnyAuthorsAsset() {
        MediaAsset media = pending(null);
        media.markUploaded();
        found(media);
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatCode(() -> verifier.validateAuthoringMedia(media.getPublicId(), MediaConstants.AUDIO_PROMPT,
                admin)).doesNotThrowAnyException();
    }

    @Test
    void validatePracticeResponseAudio_missingIds_isNotFound() {
        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(null, UUID.randomUUID(), UUID.randomUUID(),
                owner)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(UUID.randomUUID(), null, UUID.randomUUID(),
                owner)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(UUID.randomUUID(), UUID.randomUUID(), null,
                owner)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null)).isInstanceOf(MediaNotFoundException.class);
    }

    private MediaAsset readyRecording(UUID session, UUID item) {
        MediaAsset media = pending(PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE);
        media.markUploaded();
        media.setPracticeSessionPublicId(session);
        media.setPracticeItemPublicId(item);
        media.setSizeBytes(2_000L);
        media.setDurationSeconds(20);
        found(media);
        return media;
    }

    @Test
    void validatePracticeResponseAudio_matchingSessionAndItem_checksBindingWithoutError() {
        UUID session = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        MediaAsset media = readyRecording(session, item);

        verifier.validatePracticeResponseAudio(media.getPublicId(), session, item, owner);

        verify(bindingPort).assertCanUseResponseAudio(session, item, owner);
    }

    @Test
    void validatePracticeResponseAudio_recordingOfAnotherSession_isNotFound() {
        UUID item = UUID.randomUUID();
        MediaAsset media = readyRecording(UUID.randomUUID(), item);

        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(media.getPublicId(), UUID.randomUUID(),
                item, owner)).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void validatePracticeResponseAudio_nonWavRecording_isNotFound() {
        UUID session = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        MediaAsset media = readyRecording(session, item);
        media.setContentType("audio/mpeg");

        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(media.getPublicId(), session, item, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void validatePracticeResponseAudio_recordingLargerThanTheLimit_isNotFound() {
        UUID session = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        MediaAsset media = readyRecording(session, item);
        media.setSizeBytes(MediaConstants.MAX_PRACTICE_RESPONSE_BYTES + 1);

        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(media.getPublicId(), session, item, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void validatePracticeResponseAudio_recordingNotYetUploaded_isNotFound() {
        UUID session = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        MediaAsset media = readyRecording(session, item);
        media.setStatus(MediaStatus.PENDING_UPLOAD);

        assertThatThrownBy(() -> verifier.validatePracticeResponseAudio(media.getPublicId(), session, item, owner))
                .isInstanceOf(MediaNotFoundException.class);
    }
}
