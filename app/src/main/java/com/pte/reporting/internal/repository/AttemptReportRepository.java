package com.pte.reporting.internal.repository;

import com.pte.reporting.domain.AttemptReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttemptReportRepository extends JpaRepository<AttemptReport, Long> {

    Optional<AttemptReport> findByAttemptPublicId(UUID attemptPublicId);
}
