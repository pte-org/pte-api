package com.pte.media.repository;

import com.pte.media.domain.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaObjectRepository extends JpaRepository<MediaObject, Long> {

    Optional<MediaObject> findByPublicId(UUID publicId);

    Optional<MediaObject> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);
}
