package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Custom query methods for QuestionRepository.
 * Extracted to keep QuestionRepository under 300 lines per coding standards.
 */
@NoRepositoryBean
public interface QuestionRepositoryCustom {

    /**
     * Find question page by CEFR level with optional filters.
     */
    @Query(value = """
            SELECT q.publicId
            FROM Question q
            WHERE q.deleted = false
              AND (:taskTypeKey IS NULL OR q.taskTypeKey = :taskTypeKey)
              AND (:section IS NULL OR q.taskTypeSection = :section)
              AND (:status IS NULL OR q.status = :status)
              AND (:cefrLevel IS NULL OR q.cefrLevel = :cefrLevel)
              AND (:pool IS NULL
                   OR q.availabilityFlagsExplicit = false AND q.pool = :pool
                   OR q.availabilityFlagsExplicit = true
                      AND (:pool = com.pte.itembank.domain.enums.QuestionPool.EXAM AND q.availableForExam = true
                           OR :pool = com.pte.itembank.domain.enums.QuestionPool.PRACTICE
                              AND q.availableForPractice = true))
              AND (:query = ''
                   OR q.promptText IS NOT NULL AND LOWER(q.promptText) LIKE CONCAT('%', :query, '%')
                   OR :publicIdQuery IS NOT NULL AND q.publicId = :publicIdQuery)
            """,
            countQuery = """
            SELECT COUNT(q)
            FROM Question q
            WHERE q.deleted = false
              AND (:taskTypeKey IS NULL OR q.taskTypeKey = :taskTypeKey)
              AND (:section IS NULL OR q.taskTypeSection = :section)
              AND (:status IS NULL OR q.status = :status)
              AND (:cefrLevel IS NULL OR q.cefrLevel = :cefrLevel)
              AND (:pool IS NULL
                   OR q.availabilityFlagsExplicit = false AND q.pool = :pool
                   OR q.availabilityFlagsExplicit = true
                      AND (:pool = com.pte.itembank.domain.enums.QuestionPool.EXAM AND q.availableForExam = true
                           OR :pool = com.pte.itembank.domain.enums.QuestionPool.PRACTICE
                              AND q.availableForPractice = true))
              AND (:query = ''
                   OR q.promptText IS NOT NULL AND LOWER(q.promptText) LIKE CONCAT('%', :query, '%')
                   OR :publicIdQuery IS NOT NULL AND q.publicId = :publicIdQuery)
            """)
    Page<UUID> findPagePublicIdsByCefr(@Param("taskTypeKey") String taskTypeKey,
            @Param("section") String section, @Param("status") QuestionStatus status,
            @Param("pool") QuestionPool pool, @Param("query") String query,
            @Param("publicIdQuery") UUID publicIdQuery, @Param("cefrLevel") CefrLevel cefrLevel,
            Pageable pageable);

    /**
     * Find question page with optional aiGenerated filter.
     */
    @Query(value = """
            SELECT q.publicId
            FROM Question q
            WHERE q.deleted = false
              AND (:taskTypeKey IS NULL OR q.taskTypeKey = :taskTypeKey)
              AND (:section IS NULL OR q.taskTypeSection = :section)
              AND (:status IS NULL OR q.status = :status)
              AND (:cefrLevel IS NULL OR q.cefrLevel = :cefrLevel)
              AND (:aiGenerated IS NULL
                   OR :aiGenerated = true AND q.generationMeta IS NOT NULL
                   OR :aiGenerated = false AND q.generationMeta IS NULL)
              AND (:pool IS NULL
                   OR q.availabilityFlagsExplicit = false AND q.pool = :pool
                   OR q.availabilityFlagsExplicit = true
                      AND (:pool = com.pte.itembank.domain.enums.QuestionPool.EXAM AND q.availableForExam = true
                           OR :pool = com.pte.itembank.domain.enums.QuestionPool.PRACTICE
                              AND q.availableForPractice = true))
              AND (:query = ''
                   OR q.promptText IS NOT NULL AND LOWER(q.promptText) LIKE CONCAT('%', :query, '%')
                   OR :publicIdQuery IS NOT NULL AND q.publicId = :publicIdQuery)
            """,
            countQuery = """
            SELECT COUNT(q)
            FROM Question q
            WHERE q.deleted = false
              AND (:taskTypeKey IS NULL OR q.taskTypeKey = :taskTypeKey)
              AND (:section IS NULL OR q.taskTypeSection = :section)
              AND (:status IS NULL OR q.status = :status)
              AND (:cefrLevel IS NULL OR q.cefrLevel = :cefrLevel)
              AND (:aiGenerated IS NULL
                   OR :aiGenerated = true AND q.generationMeta IS NOT NULL
                   OR :aiGenerated = false AND q.generationMeta IS NULL)
              AND (:pool IS NULL
                   OR q.availabilityFlagsExplicit = false AND q.pool = :pool
                   OR q.availabilityFlagsExplicit = true
                      AND (:pool = com.pte.itembank.domain.enums.QuestionPool.EXAM AND q.availableForExam = true
                           OR :pool = com.pte.itembank.domain.enums.QuestionPool.PRACTICE
                              AND q.availableForPractice = true))
              AND (:query = ''
                   OR q.promptText IS NOT NULL AND LOWER(q.promptText) LIKE CONCAT('%', :query, '%')
                   OR :publicIdQuery IS NOT NULL AND q.publicId = :publicIdQuery)
            """)
    Page<UUID> findPagePublicIdsByAiGenerated(@Param("taskTypeKey") String taskTypeKey,
            @Param("section") String section, @Param("status") QuestionStatus status,
            @Param("pool") QuestionPool pool, @Param("query") String query,
            @Param("publicIdQuery") UUID publicIdQuery, @Param("cefrLevel") CefrLevel cefrLevel,
            @Param("aiGenerated") Boolean aiGenerated, Pageable pageable);
}
