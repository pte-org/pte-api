package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
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

    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q WHERE q.deleted = false "
            + "AND q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED")
    List<Question> findAllWithOptions();

    @Query("SELECT q.pteTaskType, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false AND q.visibility = :visibility "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.current = true "
            + "AND q.pteTaskType IN :taskTypes GROUP BY q.pteTaskType")
    List<Object[]> countByVisibilityAndTaskTypeIn(@Param("visibility") Visibility visibility,
                                                    @Param("taskTypes") Set<PteTaskType> taskTypes);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.deleted = false "
            + "AND q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.current = true "
            + "AND q.pteTaskType = :taskType")
    long countAvailableByTaskType(@Param("taskType") PteTaskType taskType);

    /**
     * Exam-generation pool count, grouped by task type. Literal
     * {@code visibility = 'SHARED'} — the generation pool never depends on
     * which tenant's host is asking (Plan B, 2026-09-17 platform-only decision).
     */
    @Query(value = """
            SELECT pte_task_type AS taskType, COUNT(*) AS count
            FROM questions
            WHERE status = 'APPROVED' AND visibility = 'SHARED' AND is_current = true AND pte_task_type IN (:taskTypes)
            GROUP BY pte_task_type
            """, nativeQuery = true)
    List<TaskTypeCountProjection> countPublishedSharedGroupedByTaskType(@Param("taskTypes") Set<String> taskTypes);

    /** At most {@code n} random APPROVED+SHARED ids for one task type — the exam-generation draw. */
    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE status = 'APPROVED' AND visibility = 'SHARED' AND is_current = true AND pte_task_type = :taskType
            ORDER BY random()
            LIMIT :n
            """, nativeQuery = true)
    List<UUID> randomPublishedSharedIdsByTaskType(@Param("taskType") String taskType, @Param("n") int n);

    /** Stable candidate order for the assessment generator to shuffle with a persisted seed. */
    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE status = 'APPROVED' AND visibility = 'SHARED' AND is_current = true AND pte_task_type = :taskType
            ORDER BY public_id
            """, nativeQuery = true)
    List<UUID> publishedSharedIdsByTaskType(@Param("taskType") String taskType);
}
