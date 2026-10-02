package com.pte.media.internal.repository;

import com.pte.media.domain.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaObjectRepository extends JpaRepository<MediaObject, Long> {

    Optional<MediaObject> findByPublicId(UUID publicId);

    Optional<MediaObject> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    @Query("select media from MediaObject media where media.publicId in :publicIds "
            + "and (media.tenantId = :tenantId or media.tenantId is null)")
    List<MediaObject> findAllForTrustedTenant(@Param("publicIds") Collection<UUID> publicIds,
            @Param("tenantId") UUID tenantId);
}
