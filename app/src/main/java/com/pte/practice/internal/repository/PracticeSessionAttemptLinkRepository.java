package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeSessionAttemptLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionAttemptLinkRepository extends JpaRepository<PracticeSessionAttemptLink, Long> {

    Optional<PracticeSessionAttemptLink> findByPracticeSessionIdAndSourceType(
            Long practiceSessionId, com.pte.practice.internal.domain.enums.PracticeSessionSourceType sourceType);

    boolean existsByExamAttemptPublicIdAndSourceType(UUID examAttemptPublicId,
            com.pte.practice.internal.domain.enums.PracticeSessionSourceType sourceType);
}
