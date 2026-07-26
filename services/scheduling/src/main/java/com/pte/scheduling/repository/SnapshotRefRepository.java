package com.pte.scheduling.repository;

import com.pte.scheduling.domain.SnapshotRef;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SnapshotRefRepository extends JpaRepository<SnapshotRef, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<SnapshotRef> findWithItemsBySnapshotPublicId(UUID snapshotPublicId);

    boolean existsBySnapshotPublicId(UUID snapshotPublicId);
}
