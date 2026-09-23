package com.pte.media.internal.service;

import com.pte.media.domain.MediaObject;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.internal.dto.request.CloudinaryUploadRequest;
import com.pte.media.internal.dto.response.CloudinaryUploadResponse;
import com.pte.media.internal.repository.MediaObjectRepository;
import com.pte.media.internal.constant.MediaConstants;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryMediaServiceTest {

    @Mock
    private MediaObjectRepository repository;

    @Test
    void studentResponseUploadUsesSubmissionFolderAndStudentOwnership() {
        when(repository.save(any(MediaObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID studentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CloudinaryMediaService service = service();
        CloudinaryUploadResponse response = service.requestUpload(
                new CloudinaryUploadRequest(MediaConstants.AUDIO_WAV, MediaConstants.STUDENT_RESPONSE_AUDIO, 1024L),
                new CurrentUser(studentId, tenantId, List.of("STUDENT")));

        assertThat(response.folder()).isEqualTo("pte/submissions");
        assertThat(response.publicId()).startsWith("pte/submissions/");
        assertThat(response.resourceType()).isEqualTo("video");
        assertThat(response.uploadUrl()).endsWith("/video/upload");

        ArgumentCaptor<MediaObject> captor = ArgumentCaptor.forClass(MediaObject.class);
        verify(repository).save(captor.capture());
        MediaObject media = captor.getValue();
        assertThat(media.getOwnerPublicId()).isEqualTo(studentId);
        assertThat(media.getTenantId()).isEqualTo(tenantId);
        assertThat(media.isAudioPrompt()).isFalse();
        assertThat(media.getCloudinaryPublicId()).isEqualTo(response.publicId());
    }

    @Test
    void studentCannotRequestAuthoringMedia() {
        CloudinaryMediaService service = service();

        assertThatThrownBy(() -> service.requestUpload(
                new CloudinaryUploadRequest(MediaConstants.AUDIO_WAV, MediaConstants.AUDIO_PROMPT, 1024L),
                new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("STUDENT"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void hostCannotRequestStudentResponseUpload() {
        CloudinaryMediaService service = service();

        assertThatThrownBy(() -> service.requestUpload(
                new CloudinaryUploadRequest(MediaConstants.AUDIO_WAV, MediaConstants.STUDENT_RESPONSE_AUDIO, 1024L),
                new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void trustedBulkResolutionUsesOneTenantScopedQueryAndClampsTtl() {
        UUID tenantId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        MediaObject first = uploaded(firstId, tenantId, "https://media.test/first");
        MediaObject second = uploaded(secondId, null, "https://media.test/second");
        when(repository.findAllForTrustedTenant(List.of(firstId, secondId), tenantId))
                .thenReturn(List.of(first, second));

        Map<UUID, com.pte.media.dto.response.PresignedDownloadResponse> resolved = service()
                .resolveForTrustedCallers(List.of(firstId, firstId, secondId), 99_999, tenantId);

        assertThat(resolved).containsOnlyKeys(firstId, secondId);
        assertThat(resolved.get(firstId).url()).isEqualTo("https://media.test/first");
        assertThat(resolved.get(firstId).expiresInSeconds()).isEqualTo(900);
        verify(repository, times(1)).findAllForTrustedTenant(List.of(firstId, secondId), tenantId);
    }

    @Test
    void trustedBulkResolutionFailsClosedWhenARequestedMediaIsMissingOrNotUploaded() {
        UUID tenantId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(repository.findAllForTrustedTenant(List.of(missingId), tenantId)).thenReturn(List.of());

        assertThatThrownBy(() -> service().resolveForTrustedCallers(List.of(missingId), 60, tenantId))
                .isInstanceOf(com.pte.media.internal.exception.MediaNotFoundException.class);

        UUID pendingId = UUID.randomUUID();
        MediaObject pending = uploaded(pendingId, tenantId, "https://media.test/pending");
        pending.setStatus(MediaStatus.PENDING_UPLOAD);
        when(repository.findAllForTrustedTenant(List.of(pendingId), tenantId)).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service().resolveForTrustedCallers(List.of(pendingId), 60, tenantId))
                .isInstanceOf(com.pte.media.internal.exception.MediaNotYetUploadedException.class);
    }

    @Test
    void trustedBulkResolutionUsesTenantFilterAndRejectsCrossTenantRows() {
        UUID tenantId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        when(repository.findAllForTrustedTenant(List.of(mediaId), tenantId)).thenReturn(List.of());

        assertThatThrownBy(() -> service().resolveForTrustedCallers(List.of(mediaId), 60, tenantId))
                .isInstanceOf(com.pte.media.internal.exception.MediaNotFoundException.class);
        verify(repository).findAllForTrustedTenant(List.of(mediaId), tenantId);
    }

    private MediaObject uploaded(UUID publicId, UUID tenantId, String secureUrl) {
        MediaObject media = new MediaObject();
        media.setPublicId(publicId);
        media.setTenantId(tenantId);
        media.setCloudinaryPublicId("cloudinary/" + publicId);
        media.setSecureUrl(secureUrl);
        media.setStatus(MediaStatus.UPLOADED);
        return media;
    }

    private CloudinaryMediaService service() {
        return new CloudinaryMediaService(repository, "test-cloud", "test-key", "test-secret",
                "pte/authoring", "pte/submissions");
    }
}
