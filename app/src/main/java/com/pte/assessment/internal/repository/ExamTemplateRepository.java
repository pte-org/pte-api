package com.pte.assessment.internal.repository;

import com.pte.assessment.domain.ExamTemplate;
import com.pte.assessment.domain.enums.TemplateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamTemplateRepository extends JpaRepository<ExamTemplate, Long> {

    Optional<ExamTemplate> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ExamTemplate t WHERE t.publicId = :publicId")
    Optional<ExamTemplate> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    List<ExamTemplate> findAllByOrderByCreatedAtDesc();

    List<ExamTemplate> findByStatusOrderByCreatedAtDesc(TemplateStatus status);
}
