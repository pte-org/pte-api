package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.PinnedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PinnedItemRepository extends JpaRepository<PinnedItem, Long> {

    Optional<PinnedItem> findByPinnedSnapshotIdAndOrderIndex(Long pinnedSnapshotId, int orderIndex);

    Optional<PinnedItem> findByPublicId(UUID publicId);

    long countByPinnedSnapshotId(Long pinnedSnapshotId);
}
