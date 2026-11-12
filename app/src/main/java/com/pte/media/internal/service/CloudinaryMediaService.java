package com.pte.media.internal.service;

import com.pte.media.internal.constant.MediaConstants;
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
import org.springframework.web.client.RestClient;

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
    static final Set<String> AUDIO_TYPES = Set.of(MediaConstants.AUDIO_WAV, MediaConstants.AUDIO_MPEG);
    /** Student recordings feed scoring, which only decodes WAV. */
    static final Set<String> RESPONSE_AUDIO_TYPES = Set.of(MediaConstants.AUDIO_WAV);
    static final long SIGNATURE_TTL_SECONDS = 900;

    private final CloudinaryUploadRequester uploadRequester;
    private final CloudinaryUploadVerifier uploadVerifier;
    private final CloudinaryMediaReader mediaReader;
    private final CloudinaryGeneratedUploader generatedUploader;

    CloudinaryMediaService(MediaAssetRepository repository, String cloudName, String apiKey, String apiSecret,
            String rootFolder, PracticeMediaBindingPort practiceMediaBindingService,
            AuditLogService auditLogService, Clock clock, RestClient.Builder restClientBuilder) {
        this(repository, cloudName, apiKey, apiSecret, rootFolder, SIGNATURE_TTL_SECONDS,
                practiceMediaBindingService, auditLogService, clock, restClientBuilder);
    }

    @Autowired
    public CloudinaryMediaService(MediaAssetRepository repository,
            @Value("${media.cloudinary.cloud-name:}") String cloudName,
            @Value("${media.cloudinary.api-key:}") String apiKey,
            @Value("${media.cloudinary.api-secret:}") String apiSecret,
            @Value("${media.cloudinary.root-folder:" + MediaConstants.DEFAULT_CLOUDINARY_ROOT_FOLDER + "}")
            String rootFolder,
            @Value("${media.cloudinary.signature-ttl-seconds:" + SIGNATURE_TTL_SECONDS + "}") long signatureTtlSeconds,
            PracticeMediaBindingPort practiceMediaBindingService, AuditLogService auditLogService, Clock clock,
            RestClient.Builder restClientBuilder) {
        CloudinaryRequestSigner signer = new CloudinaryRequestSigner(cloudName, apiKey, apiSecret, clock);
        MediaAccessGuard accessGuard = new MediaAccessGuard(practiceMediaBindingService, auditLogService);
        CloudinaryFolders folders = new CloudinaryFolders(rootFolder);
        this.uploadRequester = new CloudinaryUploadRequester(repository, signer, accessGuard,
                practiceMediaBindingService, folders, signatureTtlSeconds, clock);
        this.uploadVerifier = new CloudinaryUploadVerifier(repository, signer, accessGuard);
        this.mediaReader = new CloudinaryMediaReader(repository, signer, signatureTtlSeconds);
        this.generatedUploader = new CloudinaryGeneratedUploader(repository, signer, folders, clock,
                restClientBuilder);
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

    /** Measured length of each given media object that has one, from one repository query. */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> durationSeconds(Collection<UUID> mediaPublicIds) {
        return mediaReader.durationSeconds(mediaPublicIds);
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

    /** Server-side upload of AI-generated media (audio or image) for questions. */
    @Transactional
    public UUID storeGenerated(byte[] bytes, String contentType, String assetKind, String pool, CurrentUser owner) {
        return generatedUploader.storeGenerated(bytes, contentType, assetKind, pool, owner);
    }

    /** Removes AI-generated media the caller generated, at Cloudinary and in the database. */
    @Transactional
    public void deleteGenerated(UUID mediaPublicId, CurrentUser owner) {
        generatedUploader.deleteGenerated(mediaPublicId, owner);
    }
}
