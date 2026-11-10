package com.pte.scoring.internal.rubric.repository;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringRubricVersionRepository extends JpaRepository<ScoringRubricVersion, Long> {

    Optional<ScoringRubricVersion> findByPublicId(UUID publicId);

    /** At most one row matches ACTIVE (partial unique index); never call with another status. */
    Optional<ScoringRubricVersion> findByStatus(RubricVersionStatus status);

    /** The public id only, so reading the ACTIVE id does not put a soon-to-be-stale entity in the context. */
    @Query("select v.publicId from ScoringRubricVersion v where v.status = :status")
    Optional<UUID> findPublicIdByStatus(@Param("status") RubricVersionStatus status);

    List<ScoringRubricVersion> findAllByOrderByVersionNoDesc();

    @Query(value = "select coalesce(max(version_no), 0) from scoring_rubric_versions", nativeQuery = true)
    int findMaxVersionNo();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ScoringRubricVersion v where v.status = :status")
    Optional<ScoringRubricVersion> lockActive(@Param("status") RubricVersionStatus status);

    /**
     * Retires the current ACTIVE row with a native statement touching only the columns the V32 trigger allows on
     * ACTIVE -> RETIRED. Run and flushed before the new version is set ACTIVE, so the single-ACTIVE unique index
     * never sees two ACTIVE rows inside the activation transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update scoring_rubric_versions
               set status = 'RETIRED', retired_at = :now, updated_at = :now, lock_version = lock_version + 1
             where status = 'ACTIVE'
            """, nativeQuery = true)
    int retireActive(@Param("now") Instant now);
}
