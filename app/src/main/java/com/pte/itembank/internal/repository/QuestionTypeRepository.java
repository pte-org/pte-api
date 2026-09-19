package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.QuestionTypeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionTypeRepository extends JpaRepository<QuestionTypeDefinition, Long> {

    List<QuestionTypeDefinition> findAllByDeletedFalseOrderByDisplayOrderAscCodeAsc();

    List<QuestionTypeDefinition> findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscCodeAsc();

    Optional<QuestionTypeDefinition> findByCodeAndDeletedFalse(String code);

    /** Includes soft-deleted rows so a deleted standard type can be restored. */
    Optional<QuestionTypeDefinition> findByCode(String code);

    Optional<QuestionTypeDefinition> findByPublicIdAndDeletedFalse(UUID publicId);

    @Query("SELECT COALESCE(MAX(q.displayOrder), 0) FROM QuestionTypeDefinition q WHERE q.deleted = false")
    int findMaxDisplayOrder();
}
