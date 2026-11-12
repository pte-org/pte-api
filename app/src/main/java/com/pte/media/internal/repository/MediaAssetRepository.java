package com.pte.media.internal.repository;

import com.pte.media.domain.MediaAsset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByPublicId(UUID publicId);

    Optional<MediaAsset> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /** Measured length of each given media object that has one, as {@code [publicId, durationSeconds]}. */
    @Query("select media.publicId, media.durationSeconds from MediaAsset media "
            + "where media.publicId in :publicIds and media.durationSeconds is not null")
    List<Object[]> findDurationsByPublicIds(@Param("publicIds") Collection<UUID> publicIds);

    /**
     * Next page of practice recordings under {@code rootPrefix} uploaded before {@code cutoff}, in id order after
     * {@code afterId}. The prefix keeps each environment to its own files when several share one Cloudinary account.
     */
    @Query("select media from MediaAsset media where media.practiceItemPublicId is not null "
            + "and media.storageKey like :rootPrefix "
            + "and media.createdAt < :cutoff and media.id > :afterId order by media.id")
    List<MediaAsset> findPracticeAudioPage(@Param("rootPrefix") String rootPrefix, @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId, Pageable page);

    /**
     * Next page of question files (prompt audio or an image) under {@code rootPrefix} uploaded before {@code cutoff},
     * after {@code afterId}. Students' answers are never included: they are neither prompt audio nor images.
     */
    @Query("select media from MediaAsset media where (media.audioPrompt = true or media.contentType like 'image/%') "
            + "and media.practiceItemPublicId is null and media.storageKey like :rootPrefix "
            + "and media.createdAt < :cutoff and media.id > :afterId order by media.id")
    List<MediaAsset> findAuthoringMediaPage(@Param("rootPrefix") String rootPrefix,
            @Param("cutoff") Instant cutoff, @Param("afterId") long afterId, Pageable page);

    @Query("select media from MediaAsset media where media.publicId in :publicIds "
            + "and (media.tenantId = :tenantId or media.tenantId is null)")
    List<MediaAsset> findAllForTrustedTenant(@Param("publicIds") Collection<UUID> publicIds,
            @Param("tenantId") UUID tenantId);
}
