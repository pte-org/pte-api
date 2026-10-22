package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeExplanationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PracticeExplanationOutboxRepository extends JpaRepository<PracticeExplanationOutbox, Long> {

    List<PracticeExplanationOutbox> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualAndDeletedFalseOrderByCreatedAtAsc(
            Instant now, Pageable pageable);
}
