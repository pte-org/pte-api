package com.pte.media.internal.service;

import com.pte.media.domain.MediaObject;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    private CloudinaryMediaService service() {
        return new CloudinaryMediaService(repository, "test-cloud", "test-key", "test-secret",
                "pte/authoring", "pte/submissions");
    }
}
