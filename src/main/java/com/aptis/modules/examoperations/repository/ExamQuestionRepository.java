package com.aptis.modules.examoperations.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.aptis.modules.examoperations.domain.ExamQuestion;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {
    boolean existsByQuestionIdAndIsUsedInAssignedBatchTrue(Long questionId);

    List<ExamQuestion> findByExamIdOrderBySequenceAsc(Long examId);
}
