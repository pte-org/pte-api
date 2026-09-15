package com.pte.admin.repository;

import com.pte.admin.domain.ProjectionBackfill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ProjectionBackfillRepository extends JpaRepository<ProjectionBackfill, String> {

    /**
     * Claims a named backfill for this instance. Returns 1 for the single
     * winner and 0 for everyone else, so concurrently booting replicas cannot
     * all run the same rebuild: the primary key decides the winner in the
     * database rather than in a lost-update race between count-then-write.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO projection_backfills (name, claimed_at)
            VALUES (:name, :claimedAt)
            ON CONFLICT (name) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("name") String name, @Param("claimedAt") Instant claimedAt);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE projection_backfills
            SET completed_at = :completedAt, rows_applied = :rowsApplied
            WHERE name = :name
            """, nativeQuery = true)
    int markCompleted(@Param("name") String name, @Param("completedAt") Instant completedAt,
            @Param("rowsApplied") int rowsApplied);

    /** Releases a failed claim so a later boot retries instead of skipping forever. */
    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM projection_backfills
            WHERE name = :name AND completed_at IS NULL
            """, nativeQuery = true)
    int releaseClaim(@Param("name") String name);
}
