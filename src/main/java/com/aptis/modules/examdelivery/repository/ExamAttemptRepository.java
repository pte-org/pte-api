package com.aptis.modules.examdelivery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptis.modules.examdelivery.domain.ExamAttempt;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    @EntityGraph(attributePaths = {"answers"})
    @Query("SELECT DISTINCT ea FROM ExamAttempt ea JOIN ea.exam e WHERE ea.isSubmitted = true AND e.organizationId IN :organizationIds ORDER BY ea.submittedAt DESC")
    List<ExamAttempt> findSubmittedByOrgIds(@Param("organizationIds") List<Long> organizationIds);

    // Loads exam association to avoid LazyInitializationException in validateAttemptScope
    @EntityGraph(attributePaths = {"exam"})
    @Query("SELECT ea FROM ExamAttempt ea WHERE ea.id = :id")
    Optional<ExamAttempt> findByIdWithExam(@Param("id") Long id);
}
