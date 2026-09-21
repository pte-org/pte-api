package com.pte.assessment;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.ExamGenerationService;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code assessment}. {@code
 * SnapshotPublishService}, repositories, and controllers stay in
 * {@code internal/}.
 *
 * <p>Exposes read methods known to have cross-module callers:
 * {@code session} (Phase 06) validating composition against a published
 * snapshot's summary, {@code attempt} (Phase 07) pinning full content at
 * attempt-create, and {@code reporting} (Phase 10) reading immutable snapshot
 * weights. Generation is also exposed here so future session creation does not
 * reach assessment internals.
 */
@Service
public class AssessmentService {

    private final SnapshotPublishService snapshotPublishService;
    private final ExamGenerationService examGenerationService;
    private final ScoreTemplateService scoreTemplateService;

    @Autowired
    public AssessmentService(SnapshotPublishService snapshotPublishService, ExamGenerationService examGenerationService,
            ScoreTemplateService scoreTemplateService) {
        this.snapshotPublishService = snapshotPublishService;
        this.examGenerationService = examGenerationService;
        this.scoreTemplateService = scoreTemplateService;
    }

    /** Compatibility constructor for focused assessment unit tests. */
    public AssessmentService(SnapshotPublishService snapshotPublishService, ExamGenerationService examGenerationService) {
        this.snapshotPublishService = snapshotPublishService;
        this.examGenerationService = examGenerationService;
        this.scoreTemplateService = null;
    }

    /** Answer-stripped summary - safe for {@code session} to validate composition against. */
    public SnapshotResponse getSummary(UUID snapshotPublicId) {
        return snapshotPublishService.getSummary(snapshotPublicId);
    }

    /** Full-fidelity content including answer keys - trusted application call only. */
    public SnapshotContentResponse getFullContent(UUID snapshotPublicId) {
        return snapshotPublishService.getContent(snapshotPublicId);
    }

    /**
     * The only entry point {@code session} (Phase 3) uses to create an exam:
     * random-draws a blueprint from the question bank shaped by the ACTIVE
     * {@code ScoreTemplate} and publishes it, all in one transaction.
     */
    public SnapshotResponse generateAndPublish(String name, Set<String> skills, CurrentUser caller) {
        return examGenerationService.generate(name, skills, caller);
    }

    /** Generates one immutable, template-pinned snapshot from a persisted seed. */
    public SnapshotResponse generateDeterministic(String name, UUID templatePublicId, long seed,
            CurrentUser caller) {
        return examGenerationService.generateDeterministic(name, templatePublicId, seed, caller);
    }

    /** Safe, answer-free readiness report used by authoring/admin screens. */
    @Transactional(readOnly = true)
    public ScoreTemplateFeasibilityResponse getTemplateFeasibility(UUID templatePublicId) {
        if (scoreTemplateService == null) {
            throw new IllegalStateException("Template feasibility dependencies are not configured");
        }
        return scoreTemplateService.getTemplateFeasibility(templatePublicId);
    }
}
