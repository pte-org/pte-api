package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface QuestionRepository extends JpaRepository<Question, Long>, QuestionRepositoryCustom {

    @EntityGraph(attributePaths = "options")
    @Query("select q from Question q where q.publicId = :publicId and q.deleted = false")
    Optional<Question> findWithOptionsByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.publicId = :publicId")
    Optional<Question> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    /** The question a generation job already produced for one slot (idempotent retry after a crash). */
    Optional<Question> findByGenerationJobPublicIdAndGenerationItemIndex(UUID jobPublicId, Integer itemIndex);

    /** Whether a live question of the task type already carries this normalized-content hash. */
    boolean existsByTaskTypeKeyAndContentHashAndDeletedFalse(String taskTypeKey, String contentHash);

    /** True when another question was opened as a revision of this one. */
    boolean existsBySupersedesPublicId(UUID supersedesPublicId);

    /** Another live question that plays the same audio, so a shared file is not removed with one of them. */
    boolean existsByAudioPromptRefAndDeletedFalseAndPublicIdNot(UUID audioPromptRef, UUID publicId);

    /** Another live question that shows the same image. */
    boolean existsByImagePromptRefAndDeletedFalseAndPublicIdNot(UUID imagePromptRef, UUID publicId);

    @EntityGraph(attributePaths = "options")
    @Query("SELECT q FROM Question q WHERE q.deleted = false")
    List<Question> findAllWithOptions();

    /** The same page without a CEFR filter. */
    default Page<UUID> findPagePublicIds(String taskTypeKey, String section, QuestionStatus status,
            QuestionPool pool, String query, UUID publicIdQuery, Pageable pageable) {
        return findPagePublicIdsByCefr(taskTypeKey, section, status, pool, query, publicIdQuery, null, pageable);
    }

    @EntityGraph(attributePaths = "options")
    @Query("SELECT DISTINCT q FROM Question q WHERE q.deleted = false AND q.publicId IN :publicIds")
    List<Question> findWithOptionsByPublicIdIn(@Param("publicIds") Collection<UUID> publicIds);

    @Query("""
            SELECT q.publicId FROM Question q
            WHERE q.deleted = false
              AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED
              AND q.current = true
              AND (q.availableForPractice = true OR (q.availabilityFlagsExplicit = false
                   AND q.pool = com.pte.itembank.domain.enums.QuestionPool.PRACTICE))
              AND q.taskTypeKey = :taskTypeKey
            ORDER BY q.publicId
            """)
    List<UUID> findPublishedPracticeQuestionIdsByTaskTypeKey(@Param("taskTypeKey") String taskTypeKey,
            Pageable pageable);

    /** Number of practice-ready questions per task type key, same pool rule as {@link #findPublishedPracticeQuestionIdsByTaskTypeKey}. */
    @Query("""
            SELECT q.taskTypeKey, COUNT(q) FROM Question q
            WHERE q.deleted = false
              AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED
              AND q.current = true
              AND (q.availableForPractice = true OR (q.availabilityFlagsExplicit = false
                   AND q.pool = com.pte.itembank.domain.enums.QuestionPool.PRACTICE))
              AND q.taskTypeKey IN :taskTypeKeys
            GROUP BY q.taskTypeKey
            """)
    List<Object[]> countPublishedPracticeQuestionsByTaskTypeKeys(@Param("taskTypeKeys") Collection<String> taskTypeKeys);

    @Query("""
            select case when count(q) > 0 then true else false end from Question q
             where q.deleted = false and q.status = :status
               and q.current = true
               and (q.availableForPractice = true OR (q.availabilityFlagsExplicit = false and q.pool = :pool))
               and q.taskTypeKey = :taskTypeKey
            """)
    boolean existsByDeletedFalseAndStatusAndCurrentTrueAndPoolAndTaskTypeKey(
            @Param("status") QuestionStatus status,
            @Param("pool") QuestionPool pool, @Param("taskTypeKey") String taskTypeKey);

    long countByDeletedFalse();

    /** True when some other revision in the group already holds the group's one "current" slot. */
    boolean existsByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(UUID revisionGroupPublicId,
            UUID publicId);

    /** Any other revision(s) in the group currently holding the "current" slot — normally at most one. */
    List<Question> findByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(UUID revisionGroupPublicId,
            UUID publicId);

    @Query("SELECT q.taskTypeSection, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false GROUP BY q.taskTypeSection")
    List<Object[]> countBySection();

    @Query("SELECT q.status, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false GROUP BY q.status")
    List<Object[]> countByStatus();

    /**
     * Overview: one grouped read of the bank by status, skill section and pool (at most
     * statuses x sections x pools rows); counts the same rows as the two stats queries above.
     */
    @Query("SELECT q.status, q.taskTypeSection, q.pool, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false "
            + "GROUP BY q.status, q.taskTypeSection, q.pool")
    List<Object[]> countByStatusSectionAndPool();

    /** Overview: questions created in {@code [from, to)}, whatever their status is now. */
    @Query("SELECT COUNT(q) FROM Question q WHERE q.deleted = false "
            + "AND q.createdAt >= :from AND q.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from,
            @Param("to") Instant to);
    /** Every exam-ready question of a task type with its CEFR level, as {@code [publicId, cefrLevel]}, in a fixed order. */
    @Query("SELECT q.publicId, q.cefrLevel FROM Question q WHERE q.deleted = false "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.current = true AND (q.availableForExam = true OR (q.availabilityFlagsExplicit = false AND q.pool = com.pte.itembank.domain.enums.QuestionPool.EXAM)) "
            + "AND q.taskTypeKey = :taskTypeKey ORDER BY q.publicId")
    List<Object[]> findExamCandidatesByTaskTypeKey(@Param("taskTypeKey") String taskTypeKey);

    /** Audio prompt of every exam-ready question of the given task types, as {@code [taskTypeKey, audioPromptRef]}. */
    @Query("SELECT q.taskTypeKey, q.audioPromptRef FROM Question q WHERE q.deleted = false "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.current = true AND (q.availableForExam = true OR (q.availabilityFlagsExplicit = false AND q.pool = com.pte.itembank.domain.enums.QuestionPool.EXAM)) "
            + "AND q.audioPromptRef IS NOT NULL AND q.taskTypeKey IN :taskTypeKeys")
    List<Object[]> findExamAudioPromptRefsByTaskTypeKeys(@Param("taskTypeKeys") Collection<String> taskTypeKeys);

    @Query("SELECT q.pteTaskType, COUNT(q) FROM Question q "
            + "WHERE q.deleted = false "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.current = true AND (q.availableForExam = true OR (q.availabilityFlagsExplicit = false AND q.pool = com.pte.itembank.domain.enums.QuestionPool.EXAM)) "
            + "AND q.pteTaskType IN :taskTypes GROUP BY q.pteTaskType")
    List<Object[]> countByTaskTypeIn(@Param("taskTypes") Set<PteTaskType> taskTypes);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.deleted = false "
            + "AND q.status = com.pte.itembank.domain.enums.QuestionStatus.APPROVED "
            + "AND q.current = true AND (q.availableForExam = true OR (q.availabilityFlagsExplicit = false AND q.pool = com.pte.itembank.domain.enums.QuestionPool.EXAM)) "
            + "AND q.pteTaskType = :taskType")
    long countAvailableByTaskType(@Param("taskType") PteTaskType taskType);

    /**
     * Exam-generation pool count, grouped by task type. The pool never depends on which
     * tenant's host is asking (platform-only bank). Every exam-generation query below pins the
     * independent exam channel flag so practice-only revisions never reach an exam.
     */
    @Query(value = """
            SELECT pte_task_type AS taskType, COUNT(*) AS count
            FROM questions
            WHERE deleted = false AND status = 'APPROVED' AND is_current = true
              AND (available_for_exam = true OR (availability_flags_explicit = false AND pool = 'EXAM'))
              AND pte_task_type IN (:taskTypes)
            GROUP BY pte_task_type
            """, nativeQuery = true)
    List<TaskTypeCountProjection> countPublishedSharedGroupedByTaskType(@Param("taskTypes") Set<String> taskTypes);

    /** At most {@code n} random APPROVED+SHARED ids for one task type — the exam-generation draw. */
    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE deleted = false AND status = 'APPROVED' AND is_current = true
              AND (available_for_exam = true OR (availability_flags_explicit = false AND pool = 'EXAM')) AND pte_task_type = :taskType
            ORDER BY random()
            LIMIT :n
            """, nativeQuery = true)
    List<UUID> randomPublishedSharedIdsByTaskType(@Param("taskType") String taskType, @Param("n") int n);

    /** Stable candidate order for the assessment generator to shuffle with a persisted seed. */
    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE deleted = false AND status = 'APPROVED' AND is_current = true
              AND (available_for_exam = true OR (availability_flags_explicit = false AND pool = 'EXAM')) AND pte_task_type = :taskType
            ORDER BY public_id
            """, nativeQuery = true)
    List<UUID> publishedSharedIdsByTaskType(@Param("taskType") String taskType);

    @Query(value = """
            SELECT task_type_key AS taskType, COUNT(*) AS count
            FROM questions
            WHERE deleted = false AND status = 'APPROVED' AND is_current = true
              AND (available_for_exam = true OR (availability_flags_explicit = false AND pool = 'EXAM'))
              AND task_type_key IN (:taskTypeKeys)
            GROUP BY task_type_key
            """, nativeQuery = true)
    List<TaskTypeCountProjection> countPublishedSharedGroupedByTaskTypeKey(
            @Param("taskTypeKeys") Set<String> taskTypeKeys);

    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE deleted = false AND status = 'APPROVED' AND is_current = true
              AND (available_for_exam = true OR (availability_flags_explicit = false AND pool = 'EXAM'))
              AND task_type_key = :taskTypeKey
            ORDER BY random()
            LIMIT :n
            """, nativeQuery = true)
    List<UUID> randomPublishedSharedIdsByTaskTypeKey(@Param("taskTypeKey") String taskTypeKey,
            @Param("n") int n);

    @Query(value = """
            SELECT public_id
            FROM questions
            WHERE deleted = false AND status = 'APPROVED' AND is_current = true
              AND (available_for_exam = true OR (availability_flags_explicit = false AND pool = 'EXAM'))
              AND task_type_key = :taskTypeKey
            ORDER BY public_id
            """, nativeQuery = true)
    List<UUID> publishedSharedIdsByTaskTypeKey(@Param("taskTypeKey") String taskTypeKey);

    /**
     * Retrieves topics from the last N AI-generated questions of the same task type.
     * Used to populate the avoidTopics hint for the generator.
     */
    @Query(value = """
            SELECT generation_meta ->> 'topic'
            FROM questions
            WHERE deleted = false AND task_type_key = :taskTypeKey AND generation_meta IS NOT NULL
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<String> findRecentTopicsByTaskType(@Param("taskTypeKey") String taskTypeKey,
            Pageable pageable);
}
