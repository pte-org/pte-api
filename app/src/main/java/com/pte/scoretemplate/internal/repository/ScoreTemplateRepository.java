package com.pte.scoretemplate.internal.repository;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreTemplateRepository extends JpaRepository<ScoreTemplate, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<ScoreTemplate> findWithItemsByPublicId(UUID publicId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT DISTINCT t FROM ScoreTemplate t WHERE t.publicId IN :publicIds")
    List<ScoreTemplate> findAllWithItemsByPublicIds(@Param("publicIds") List<UUID> publicIds);

    @EntityGraph(attributePaths = "items")
    Optional<ScoreTemplate> findWithItemsByStatus(ScoreTemplateStatus status);

    List<ScoreTemplate> findAllByOrderByCodeAscVersionDesc();

    /** Next version for {@code code} = max existing version + 1 (no rows yet -> version 1). */
    @Query("SELECT COALESCE(MAX(t.version), 0) FROM ScoreTemplate t WHERE t.code = :code")
    int findMaxVersionByCode(@Param("code") String code);

    /**
     * Locks every row sharing {@code code} before {@code activate}/
     * {@code cloneToDraft} read the current ACTIVE row or compute
     * {@code max(version)} — closes the TOCTOU window between two concurrent
     * admin calls for the same template family (see phase-01 Risks).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ScoreTemplate t WHERE t.code = :code")
    List<ScoreTemplate> findAllByCodeForUpdate(@Param("code") String code);
}
