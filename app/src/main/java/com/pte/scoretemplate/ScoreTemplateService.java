package com.pte.scoretemplate;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.exception.NoActiveScoreTemplateException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotFoundException;
import com.pte.scoretemplate.internal.mapper.ScoreTemplateMapper;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The only door other modules use to reach {@code scoretemplate}. {@code
 * ScoreTemplateRepository}, the admin service, and the controller stay in
 * {@code internal/}. Read-only and unauthenticated by design — callers
 * ({@code assessment} pinning at publish, {@code attempt} for timing,
 * {@code scoring} for scoringMethod, {@code reporting} for weights) are
 * trusted in-process application code, not end users; PLATFORM_ADMIN write
 * access is enforced only at {@code ScoreTemplateController}.
 */
@Service
public class ScoreTemplateService {

    private final ScoreTemplateRepository repository;

    public ScoreTemplateService(ScoreTemplateRepository repository) {
        this.repository = repository;
    }

    /** Used by {@code assessment.SnapshotPublishService} to pin the scoring scheme at exam-publish time. */
    @Transactional(readOnly = true)
    public ScoreTemplateResponse getActive() {
        ScoreTemplate active = repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)
                .orElseThrow(NoActiveScoreTemplateException::new);
        return ScoreTemplateMapper.toResponse(active);
    }

    /**
     * Used by everything that already has a pinned {@code scoreTemplatePublicId}
     * (attempt/scoring/reporting) — deliberately does NOT filter by status, so
     * a snapshot pinned to a now-RETIRED template still resolves (immutability
     * guarantee: a published exam's scoring never drifts).
     */
    @Transactional(readOnly = true)
    public ScoreTemplateResponse getByPublicId(UUID publicId) {
        ScoreTemplate template = repository.findWithItemsByPublicId(publicId)
                .orElseThrow(ScoreTemplateNotFoundException::new);
        return ScoreTemplateMapper.toResponse(template);
    }
}
