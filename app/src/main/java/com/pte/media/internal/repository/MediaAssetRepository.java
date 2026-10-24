package com.pte.media.internal.repository;

import com.pte.media.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByPublicId(UUID publicId);

    Optional<MediaAsset> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    @Query("select media from MediaAsset media where media.publicId in :publicIds "
            + "and (media.tenantId = :tenantId or media.tenantId is null)")
    List<MediaAsset> findAllForTrustedTenant(@Param("publicIds") Collection<UUID> publicIds,
            @Param("tenantId") UUID tenantId);
}
