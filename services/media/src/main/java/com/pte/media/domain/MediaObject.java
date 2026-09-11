package com.pte.media.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.media.domain.enums.MediaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One uploaded (or pending-upload) binary asset. {@code storageKey} is the
 * MinIO/S3 object key — never exposed directly to a caller outside this
 * service; every access goes through a presigned URL with a short TTL.
 */
@Entity
@Table(name = "media_objects", indexes = {
        @Index(name = "idx_media_objects_owner", columnList = "owner_public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MediaObject extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID ownerPublicId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, unique = true)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaStatus status = MediaStatus.PENDING_UPLOAD;

    /**
     * Set at request-upload time from {@code RequestUploadRequest.audioPrompt()}
     * — {@code false} for every object created before this field existed or by
     * any caller that doesn't opt in (e.g. candidates' own recorded answers).
     * Gates both the WAV-only content-type restriction and whether
     * {@code completeUpload} attempts duration extraction
     * (plans/phat-speaking-dynamic-prep-timing).
     */
    // columnDefinition sets an explicit DEFAULT so ddl-auto:update's own
    // ADD COLUMN ... NOT NULL can succeed against a table that already has
    // rows (the bare `nullable = false` form emits no DEFAULT, which
    // Postgres rejects outright — confirmed against this project's own
    // dev database during plans/phat-speaking-dynamic-prep-timing Phase 5).
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean audioPrompt;

    /**
     * WAV duration in whole seconds, set only for {@link #audioPrompt} objects,
     * only once it's been successfully extracted at complete-upload time — never
     * a guessed/estimated value. {@code null} for every non-audio-prompt object,
     * and for an audio-prompt object that failed extraction (which never
     * reaches {@link MediaStatus#UPLOADED} at all — see
     * {@code PresignService.completeUpload}).
     */
    @Column
    private Integer durationSeconds;

    public void markUploaded() {
        this.status = MediaStatus.UPLOADED;
    }
}
