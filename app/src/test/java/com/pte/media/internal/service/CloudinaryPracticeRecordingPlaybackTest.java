package com.pte.media.internal.service;

import com.pte.media.domain.MediaAsset;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.practice.PracticeMediaConstants;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CloudinaryPracticeRecordingPlaybackTest {

    private final MediaAssetRepository repository = mock(MediaAssetRepository.class);
    private final PracticeMediaBindingPort liveBinding = mock(PracticeMediaBindingPort.class);
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("STUDENT"));
    private final UUID sessionId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();
    private final CloudinaryMediaService service = new CloudinaryMediaService(repository,
            "test-cloud", "test-key", "test-secret", "pte/authoring", "pte/submissions",
            liveBinding, null, com.pte.TestClocks.fixed());

    @Test
    void completedRecordingPlaybackChecksStoredOwnershipWithoutRequiringALiveSession() {
        MediaAsset recording = recording();
        recording.setCloudinaryDeliveryType(CloudinaryDeliveryType.AUTHENTICATED);
        when(repository.findByPublicId(mediaId)).thenReturn(Optional.of(recording));

        var result = service.presignOwnedPracticeResponseAudio(mediaId, sessionId, itemId, owner, 60);

        assertThat(result.url()).startsWith("https://api.cloudinary.com/v1_1/test-cloud/video/download?");
        assertThat(result.expiresInSeconds()).isEqualTo(60);
        verifyNoInteractions(liveBinding);
    }

    @Test
    void recordingCannotBePlayedByAnotherStudentOrFromAnotherTenantOrItem() {
        when(repository.findByPublicId(mediaId)).thenReturn(Optional.of(recording()));
        CurrentUser otherStudent = new CurrentUser(UUID.randomUUID(), owner.tenantId(), List.of("STUDENT"));
        CurrentUser otherTenant = new CurrentUser(owner.userId(), UUID.randomUUID(), List.of("STUDENT"));

        assertThatThrownBy(() -> service.presignOwnedPracticeResponseAudio(
                mediaId, sessionId, itemId, otherStudent, 60)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.presignOwnedPracticeResponseAudio(
                mediaId, sessionId, itemId, otherTenant, 60)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.presignOwnedPracticeResponseAudio(
                mediaId, sessionId, UUID.randomUUID(), owner, 60)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.presignOwnedPracticeResponseAudio(
                mediaId, UUID.randomUUID(), itemId, owner, 60)).isInstanceOf(MediaNotFoundException.class);
        verifyNoInteractions(liveBinding);
    }

    @Test
    void pendingUploadAndUnboundExamRecordingCannotBePlayedAsAPracticeRecording() {
        MediaAsset recording = recording();
        when(repository.findByPublicId(mediaId)).thenReturn(Optional.of(recording));
        recording.setStatus(MediaStatus.PENDING_UPLOAD);
        assertThatThrownBy(() -> service.presignOwnedPracticeResponseAudio(
                mediaId, sessionId, itemId, owner, 60)).isInstanceOf(MediaNotFoundException.class);

        recording.setStatus(MediaStatus.UPLOADED);
        recording.setPurpose(null);
        assertThatThrownBy(() -> service.presignOwnedPracticeResponseAudio(
                mediaId, sessionId, itemId, owner, 60)).isInstanceOf(MediaNotFoundException.class);
        verifyNoInteractions(liveBinding);
    }

    private MediaAsset recording() {
        MediaAsset recording = new MediaAsset();
        recording.setPublicId(mediaId);
        recording.setTenantId(owner.tenantId());
        recording.setOwnerPublicId(owner.userId());
        recording.setPracticeSessionPublicId(sessionId);
        recording.setPracticeItemPublicId(itemId);
        recording.setPurpose(PracticeMediaConstants.RESPONSE_AUDIO_PURPOSE);
        recording.setContentType(MediaConstants.AUDIO_WAV);
        recording.setStatus(MediaStatus.UPLOADED);
        recording.setCloudinaryPublicId("pte/submissions/" + mediaId);
        recording.setCloudinaryResourceType("video");
        recording.setSecureUrl("https://media.test/recording.wav");
        return recording;
    }
}
