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

    public void markUploaded() {
        this.status = MediaStatus.UPLOADED;
    }
}
