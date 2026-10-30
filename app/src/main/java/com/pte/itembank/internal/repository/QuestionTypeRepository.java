package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionTypeRepository extends JpaRepository<QuestionType, Long> {

    List<QuestionType> findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();

    List<QuestionType> findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc();

    Optional<QuestionType> findByCodeAndDeletedFalse(String code);

    /** Includes soft-deleted rows so a deleted standard type can be restored. */
    Optional<QuestionType> findByCode(String code);

    Optional<QuestionType> findByTaskTypeKey(String taskTypeKey);

    /** Includes retired/soft-deleted rows because display names are never reusable. */
    Optional<QuestionType> findByNormalizedDisplayName(String normalizedDisplayName);

    Optional<QuestionType> findByTaskTypeKeyAndDeletedFalse(String taskTypeKey);

    /** Includes retired/soft-deleted rows: exams already published with such a type still render it. */
    List<QuestionType> findAllByTaskTypeKeyIn(Collection<String> taskTypeKeys);

    boolean existsByTaskTypeKey(String taskTypeKey);

    boolean existsByNormalizedDisplayName(String normalizedDisplayName);

    Optional<QuestionType> findByPublicIdAndDeletedFalse(UUID publicId);

    @Query("SELECT COALESCE(MAX(q.displayOrder), 0) FROM QuestionType q WHERE q.deleted = false")
    int findMaxDisplayOrder();
}
