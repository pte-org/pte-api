package com.pte.assessment.internal.repository;

import com.pte.assessment.domain.ExamBlueprint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamBlueprintRepository extends JpaRepository<ExamBlueprint, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<ExamBlueprint> findWithItemsByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "items")
    List<ExamBlueprint> findByTenantIdIsNull();

    List<ExamBlueprint> findByTenantId(UUID tenantId);
}
