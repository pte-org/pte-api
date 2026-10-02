package com.pte.assessment.internal.repository;

import com.pte.assessment.domain.SnapshotItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SnapshotItemRepository extends JpaRepository<SnapshotItem, Long> {

    @Query("select item from SnapshotItem item join fetch item.snapshot snapshot "
            + "where item.publicId in :itemPublicIds and snapshot.tenantId = :tenantId")
    List<SnapshotItem> findAllForTenant(@Param("itemPublicIds") Collection<UUID> itemPublicIds,
            @Param("tenantId") UUID tenantId);
}
