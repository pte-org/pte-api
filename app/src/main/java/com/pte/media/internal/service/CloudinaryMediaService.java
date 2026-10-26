package com.pte.media.internal.service;

import com.pte.media.internal.dto.request.CloudinaryCompleteRequest;
import com.pte.media.internal.dto.request.CloudinaryUploadRequest;
import com.pte.media.internal.dto.response.CloudinaryUploadResponse;
import com.pte.media.internal.dto.response.MediaPreviewResponse;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.media.internal.exception.MediaNotFoundException;
import com.pte.media.internal.repository.MediaAssetRepository;
import com.pte.shared.practice.PracticeMediaBindingPort;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cloudinary direct-upload adapter for authoring media and student responses. */
@Service
public class CloudinaryMediaService implements PracticeResponseMediaValidator {

    static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    static final Set<String> AUDIO_TYPES = Set.of("audio/wav");
    static final long SIGNATURE_TTL_SECONDS = 900;

    private final CloudinaryUploadRequester uploadRequester;
    private final CloudinaryUploadVerifier uploadVerifier;
    private final CloudinaryMediaReader mediaReader;

    @Autowired
    public CloudinaryMediaService(MediaAssetRepository repository,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.authoring-folder:pte/authoring}") String authoringFolder,
            @Value("${cloudinary.submission-folder:pte/submissions}") String submissionFolder,
            PracticeMediaBindingPort practiceMediaBindingService, AuditLogService auditLogService, Clock clock) {
        CloudinaryRequestSigner signer = new CloudinaryRequestSigner(cloudName, apiKey, apiSecret, clock);
        MediaAccessGuard accessGuard = new MediaAccessGuard(practiceMediaBindingService, auditLogService);
        this.uploadRequester = new CloudinaryUploadRequester(repository, signer, accessGuard,
                practiceMediaBindingService, authoringFolder, submissionFolder, clock);
        this.uploadVerifier = new CloudinaryUploadVerifier(repository, signer, accessGuard);
        this.mediaReader = new CloudinaryMediaReader(repository, signer);
    }

    @Transactional
    public CloudinaryUploadResponse requestUpload(CloudinaryUploadRequest request, CurrentUser caller) {
        return uploadRequester.requestUpload(request, caller);
    }

    @Transactional
    public void completeUpload(UUID mediaPublicId, CloudinaryCompleteRequest request, CurrentUser caller) {
        uploadVerifier.completeUpload(mediaPublicId, request, caller);
    }

    @Transactional(readOnly = true)
    public MediaPreviewResponse preview(UUID mediaPublicId, CurrentUser caller) {
        return mediaReader.preview(mediaPublicId, caller);
    }

    /** Trusted module-to-module resolution for approved question media. */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse resolveForTrustedCaller(UUID mediaPublicId, long requestedTtlSeconds,
            UUID tenantId) {
        if (mediaPublicId == null) {
            throw new MediaNotFoundException();
        }
        return resolveForTrustedCallers(List.of(mediaPublicId), requestedTtlSeconds, tenantId).get(mediaPublicId);
    }

    /** Resolves an authorized caller's media set with one repository query. */
    @Transactional(readOnly = true)
    public Map<UUID, PresignedDownloadResponse> resolveForTrustedCallers(
            Collection<UUID> mediaPublicIds, long requestedTtlSeconds, UUID tenantId) {
        return mediaReader.resolveForTrustedCallers(mediaPublicIds, requestedTtlSeconds, tenantId);
    }

    public void validateAuthoringMedia(UUID mediaPublicId, String assetKind, CurrentUser caller) {
        uploadVerifier.validateAuthoringMedia(mediaPublicId, assetKind, caller);
    }

    /** Validates that an uploaded recording still belongs to the live practice item. */
    @Transactional(readOnly = true)
    public void validatePracticeResponseAudio(UUID mediaPublicId, UUID sessionPublicId,
            UUID itemPublicId, CurrentUser caller) {
        uploadVerifier.validatePracticeResponseAudio(mediaPublicId, sessionPublicId, itemPublicId, caller);
    }

    /** Resolves playback after practice has checked ownership of the session and response. */
    @Transactional(readOnly = true)
    public PresignedDownloadResponse presignOwnedPracticeResponseAudio(UUID mediaPublicId, UUID sessionPublicId,
            UUID itemPublicId, CurrentUser owner, long ttlSeconds) {
        return mediaReader.presignOwnedPracticeResponseAudio(mediaPublicId, sessionPublicId, itemPublicId,
                owner, ttlSeconds);
    }
}
