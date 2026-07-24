package com.pte.authoring.repository;

import com.pte.authoring.domain.ExamBlueprint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamBlueprintRepository extends JpaRepository<ExamBlueprint, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<ExamBlueprint> findWithItemsByPublicId(UUID publicId);

    List<ExamBlueprint> findByTenantId(UUID tenantId);
}
