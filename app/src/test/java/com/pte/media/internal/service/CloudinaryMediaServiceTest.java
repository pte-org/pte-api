package com.pte.media.internal.service;

import com.pte.media.domain.MediaObject;
import com.pte.media.domain.enums.MediaStatus;
import com.pte.media.domain.enums.CloudinaryDeliveryType;
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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

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
    void studentResponseUploadUsesSubmissionFolderAndStudentOwnership() throws Exception {
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
        assertThat(response.uploadUrl()).endsWith("/video/authenticated/upload");
        String uploadPayload = "folder=pte/submissions&public_id=" + response.mediaPublicId()
                + "&timestamp=" + response.timestamp();
        String expectedUploadSignature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest((uploadPayload + "test-secret").getBytes(StandardCharsets.UTF_8)));
        assertThat(response.signature()).isEqualTo(expectedUploadSignature);
        assertThat(URI.create(response.uploadUrl()).getRawQuery()).isNull();

        ArgumentCaptor<MediaObject> captor = ArgumentCaptor.forClass(MediaObject.class);
        verify(repository).save(captor.capture());
        MediaObject media = captor.getValue();
        assertThat(media.getOwnerPublicId()).isEqualTo(studentId);
        assertThat(media.getTenantId()).isEqualTo(tenantId);
        assertThat(media.isAudioPrompt()).isFalse();
        assertThat(media.getCloudinaryPublicId()).isEqualTo(response.publicId());
        assertThat(media.getCloudinaryDeliveryType()).isEqualTo(CloudinaryDeliveryType.AUTHENTICATED);
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
        assertThat(resolved.get(firstId).expiresInSeconds()).isZero();
        verify(repository, times(1)).findAllForTrustedTenant(List.of(firstId, secondId), tenantId);
    }

    @Test
    void authenticatedMediaUsesTimeLimitedSignedCloudinaryDownloadUrl() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        MediaObject media = uploaded(mediaId, tenantId,
                "https://res.cloudinary.com/test-cloud/video/authenticated/file.wav");
        media.setCloudinaryDeliveryType(CloudinaryDeliveryType.AUTHENTICATED);
        when(repository.findAllForTrustedTenant(List.of(mediaId), tenantId)).thenReturn(List.of(media));

        var resolved = service().resolveForTrustedCallers(List.of(mediaId), 60, tenantId).get(mediaId);
        URI uri = URI.create(resolved.url());
        Map<String, String> query = query(uri.getRawQuery());
        long timestamp = Long.parseLong(query.get("timestamp"));
        long expiresAt = Long.parseLong(query.get("expires_at"));
        Map<String, String> signed = new TreeMap<>();
        signed.put("attachment", "false");
        signed.put("expires_at", query.get("expires_at"));
        signed.put("format", "wav");
        signed.put("public_id", media.getCloudinaryPublicId());
        signed.put("timestamp", query.get("timestamp"));
        signed.put("type", "authenticated");
        String payload = signed.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        String expectedSignature = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest((payload + "test-secret").getBytes(StandardCharsets.UTF_8)));

        assertThat(uri.getHost()).isEqualTo("api.cloudinary.com");
        assertThat(uri.getPath()).isEqualTo("/v1_1/test-cloud/video/download");
        assertThat(query.get("type")).isEqualTo("authenticated");
        assertThat(query.get("api_key")).isEqualTo("test-key");
        assertThat(expiresAt - timestamp).isEqualTo(60);
        assertThat(query.get("signature")).isEqualTo(expectedSignature);
        assertThat(resolved.expiresInSeconds()).isEqualTo(60);
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
        media.setCloudinaryResourceType("video");
        media.setContentType(MediaConstants.AUDIO_WAV);
        media.setSecureUrl(secureUrl);
        media.setStatus(MediaStatus.UPLOADED);
        return media;
    }

    private CloudinaryMediaService service() {
        return new CloudinaryMediaService(repository, "test-cloud", "test-key", "test-secret",
                "pte/authoring", "pte/submissions");
    }

    private Map<String, String> query(String rawQuery) {
        Map<String, String> values = new java.util.HashMap<>();
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return values;
    }
}
