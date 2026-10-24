package com.pte.assessment.internal.repository;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamDefinitionRepository extends JpaRepository<ExamDefinition, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<ExamDefinition> findWithItemsByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT DISTINCT t FROM ExamDefinition t WHERE t.publicId IN :publicIds")
    List<ExamDefinition> findAllWithItemsByPublicIds(@Param("publicIds") List<UUID> publicIds);

    @EntityGraph(attributePaths = "items")
    Optional<ExamDefinition> findWithItemsByStatus(ExamDefinitionStatus status);

    List<ExamDefinition> findAllByOrderByCodeAscVersionDesc();

    /** Next version for {@code code} = max existing version + 1 (no rows yet -> version 1). */
    @Query("SELECT COALESCE(MAX(t.version), 0) FROM ExamDefinition t WHERE t.code = :code")
    int findMaxVersionByCode(@Param("code") String code);

    /**
     * Locks every row sharing {@code code} before {@code activate}/
     * {@code cloneToDraft} read the current ACTIVE row or compute
     * {@code max(version)} — closes the TOCTOU window between two concurrent
     * admin calls for the same template family (see phase-01 Risks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ExamDefinition t WHERE t.code = :code")
    List<ExamDefinition> findAllByCodeForUpdate(@Param("code") String code);
}
