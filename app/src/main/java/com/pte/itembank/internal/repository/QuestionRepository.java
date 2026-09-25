package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query(value = """
            SELECT q.publicId
            FROM Question q
            WHERE q.deleted = false
              AND q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED
              AND (:taskTypeKey IS NULL OR q.taskTypeKey = :taskTypeKey)
              AND (:section IS NULL OR q.taskTypeSection = :section)
              AND (:status IS NULL OR q.status = :status)
              AND (:query = ''
                   OR q.title IS NOT NULL AND LOWER(q.title) LIKE CONCAT('%', :query, '%')
                   OR q.promptText IS NOT NULL AND LOWER(q.promptText) LIKE CONCAT('%', :query, '%')
                   OR :publicIdQuery IS NOT NULL AND q.publicId = :publicIdQuery)
            """,
            countQuery = """
            SELECT COUNT(q)
            FROM Question q
            WHERE q.deleted = false
              AND q.visibility = com.pte.itembank.domain.enums.Visibility.SHARED
              AND (:taskTypeKey IS NULL OR q.taskTypeKey = :taskTypeKey)
              AND (:section IS NULL OR q.taskTypeSection = :section)
              AND (:status IS NULL OR q.status = :status)
              AND (:query = ''
                   OR q.title IS NOT NULL AND LOWER(q.title) LIKE CONCAT('%', :query, '%')
                   OR q.promptText IS NOT NULL AND LOWER(q.promptText) LIKE CONCAT('%', :query, '%')
                   OR :publicIdQuery IS NOT NULL AND q.publicId = :publicIdQuery)
            """)
    Page<UUID> findPagePublicIds(@Param("taskTypeKey") String taskTypeKey,
            @Param("section") String section, @Param("status") QuestionStatus status,
            @Param("query") String query, @Param("publicIdQuery") UUID publicIdQuery, Pageable pageable);

    @EntityGraph(attributePaths = "options")
    @Query("SELECT DISTINCT q FROM Question q WHERE q.publicId IN :publicIds")
    List<Question> findWithOptionsByPublicIdIn(@Param("publicIds") Collection<UUID> publicIds);

    long countByDeletedFalseAndVisibility(Visibility visibility);

    /** True when some other revision in the group already holds the group's one "current" slot. */
    boolean existsByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(UUID revisionGroupPublicId,
            UUID publicId);

    /** Any other revision(s) in the group currently holding the "current" slot — normally at most one. */
    List<Question> findByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(UUID revisionGroupPublicId,
            UUID publicId);

    @Query("SELECT q.taskTypeSection, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false AND q.visibility = :visibility GROUP BY q.taskTypeSection")
    List<Object[]> countBySectionAndVisibility(@Param("visibility") Visibility visibility);

    @Query("SELECT q.status, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false AND q.visibility = :visibility GROUP BY q.status")
    List<Object[]> countByStatusAndVisibility(@Param("visibility") Visibility visibility);

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

    @Query(value = """
            SELECT task_type_key AS taskType, COUNT(*) AS count
            FROM questions
            WHERE status = 'APPROVED' AND visibility = 'SHARED' AND is_current = true
              AND task_type_key IN (:taskTypeKeys)
            GROUP BY task_type_key
            """, nativeQuery = true)
    List<TaskTypeCountProjection> countPublishedSharedGroupedByTaskTypeKey(
            @Param("taskTypeKeys") Set<String> taskTypeKeys);

    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE status = 'APPROVED' AND visibility = 'SHARED' AND is_current = true
              AND task_type_key = :taskTypeKey
            ORDER BY random()
            LIMIT :n
            """, nativeQuery = true)
    List<UUID> randomPublishedSharedIdsByTaskTypeKey(@Param("taskTypeKey") String taskTypeKey,
            @Param("n") int n);

    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE status = 'APPROVED' AND visibility = 'SHARED' AND is_current = true
              AND task_type_key = :taskTypeKey
            ORDER BY public_id
            """, nativeQuery = true)
    List<UUID> publishedSharedIdsByTaskTypeKey(@Param("taskTypeKey") String taskTypeKey);
}
