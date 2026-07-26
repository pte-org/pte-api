package com.pte.authoring.repository;

import com.pte.authoring.domain.ExamSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamSnapshotRepository extends JpaRepository<ExamSnapshot, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<ExamSnapshot> findWithItemsByPublicId(UUID publicId);

    /** Next version number for a blueprint = count of prior snapshots + 1. */
    long countBySourceBlueprintPublicId(UUID sourceBlueprintPublicId);
}
