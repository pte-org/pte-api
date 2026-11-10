package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ActiveRubricVersionPin;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Scoring's implementation of the pin port declared by assessment (dependency stays scoring -> assessment). */
@Service
public class ActiveRubricVersionPinService implements ActiveRubricVersionPin {

    private final ScoringRubricVersionRepository versionRepository;

    public ActiveRubricVersionPinService(ScoringRubricVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> activeRubricVersionPublicId() {
        return versionRepository.findPublicIdByStatus(RubricVersionStatus.ACTIVE);
    }
}
