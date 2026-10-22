package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.PracticeExplanation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeExplanationRepository extends JpaRepository<PracticeExplanation, Long> {

    Optional<PracticeExplanation> findByIdAndDeletedFalse(Long id);

    Optional<PracticeExplanation> findByPracticeSessionItemIdAndLanguageAndGenerationSequenceAndDeletedFalse(
            Long practiceSessionItemId, String language, int generationSequence);

    Optional<PracticeExplanation> findByPublicIdAndStudentPublicIdAndDeletedFalse(
            UUID publicId, UUID studentPublicId);
}
