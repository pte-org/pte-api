package com.pte.examdelivery.repository;

import com.pte.examdelivery.domain.PinnedExamSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinnedExamSnapshotRepository extends JpaRepository<PinnedExamSnapshot, Long> {
}
