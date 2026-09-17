package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = "options")
    Optional<Question> findWithOptionsByPublicId(UUID publicId);

    /** SHARED bank + the caller's own PRIVATE items. */
    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q WHERE q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED "
            + "OR q.tenantId = :tenantId")
    List<Question> findAccessible(@Param("tenantId") UUID tenantId);

    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q")
    List<Question> findAllWithOptions();

    /**
     * Exam-generation pool count, grouped by task type. Literal
     * {@code visibility = 'SHARED'} — the generation pool never depends on
     * which tenant's host is asking (Plan B, 2026-09-17 platform-only decision).
     */
    @Query(value = """
            SELECT pte_task_type AS taskType, COUNT(*) AS count
            FROM questions
            WHERE status = 'PUBLISHED' AND visibility = 'SHARED' AND pte_task_type IN (:taskTypes)
            GROUP BY pte_task_type
            """, nativeQuery = true)
    List<TaskTypeCountProjection> countPublishedSharedGroupedByTaskType(@Param("taskTypes") Set<String> taskTypes);

    /** At most {@code n} random PUBLISHED+SHARED ids for one task type — the exam-generation draw. */
    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE status = 'PUBLISHED' AND visibility = 'SHARED' AND pte_task_type = :taskType
            ORDER BY random()
            LIMIT :n
            """, nativeQuery = true)
    List<UUID> randomPublishedSharedIdsByTaskType(@Param("taskType") String taskType, @Param("n") int n);
}
