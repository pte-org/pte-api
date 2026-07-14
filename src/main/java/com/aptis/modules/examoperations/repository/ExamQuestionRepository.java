package com.aptis.modules.examoperations.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import com.aptis.modules.examoperations.domain.ExamQuestion;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    boolean existsByQuestionIdAndIsUsedInAssignedBatchTrue(Long questionId);
}
