package com.pte.assessment;

import com.pte.assessment.dto.response.ExaminerQuestionPromptView;
import com.pte.assessment.dto.response.ResolvedExamItemResponse;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.service.ExamGenerationService;
import com.pte.assessment.internal.service.ExamItemRuntimeResolver;
import com.pte.assessment.internal.service.SnapshotPromptQueryService;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    private final ExamDefinitionService examDefinitionService;
    private final SnapshotPromptQueryService snapshotPromptQueryService;
    private final ExamItemRuntimeResolver examItemRuntimeResolver;

    @Autowired
    public AssessmentService(SnapshotPublishService snapshotPublishService, ExamGenerationService examGenerationService,
            ExamDefinitionService examDefinitionService, SnapshotPromptQueryService snapshotPromptQueryService,
            ExamItemRuntimeResolver examItemRuntimeResolver) {
        this.snapshotPublishService = snapshotPublishService;
        this.examGenerationService = examGenerationService;
        this.examDefinitionService = examDefinitionService;
        this.snapshotPromptQueryService = snapshotPromptQueryService;
        this.examItemRuntimeResolver = examItemRuntimeResolver;
    }

    /** Source-compatible constructor for focused tests that do not exercise item delivery. */
    public AssessmentService(SnapshotPublishService snapshotPublishService, ExamGenerationService examGenerationService,
            ExamDefinitionService examDefinitionService, SnapshotPromptQueryService snapshotPromptQueryService) {
        this(snapshotPublishService, examGenerationService, examDefinitionService, snapshotPromptQueryService, null);
    }

    /** Answer-stripped summary - safe for {@code session} to validate composition against. */
    public SnapshotResponse getSummary(UUID snapshotPublicId) {
        return snapshotPublishService.getSummary(snapshotPublicId);
    }

    /** Full-fidelity content including answer keys - trusted application call only. */
    public SnapshotContentResponse getFullContent(UUID snapshotPublicId) {
        return snapshotPublishService.getContent(snapshotPublicId);
    }

    /** Resolves the same runtime/timing/media seam used by student delivery and host preview. */
    @Transactional(readOnly = true)
    public List<ResolvedExamItemResponse> resolveExamItems(SnapshotContentResponse content,
            long mediaUrlTtlSeconds, UUID tenantId) {
        if (examItemRuntimeResolver == null) {
            throw new IllegalStateException("Exam item runtime resolver is not configured");
        }
        return examItemRuntimeResolver.resolve(content, mediaUrlTtlSeconds, tenantId);
    }

    /** Answer-key-free, batched prompt access after scoring verifies assignment ownership. */
    public Map<UUID, ExaminerQuestionPromptView> getExaminerPrompts(
            Collection<UUID> pinnedItemPublicIds, UUID tenantId) {
        if (snapshotPromptQueryService == null) {
            throw new IllegalStateException(AssessmentConstants.EXAMINER_PROMPT_DEPENDENCIES_NOT_CONFIGURED);
        }
        return snapshotPromptQueryService.findForExaminer(pinnedItemPublicIds, tenantId);
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

    /** Generates from the host-persisted skill scope through the assessment module boundary. */
    public SnapshotResponse generateDeterministic(String name, UUID templatePublicId, long seed,
            CurrentUser caller, Set<String> selectedSkills) {
        return examGenerationService.generateDeterministic(name, templatePublicId, seed, caller, selectedSkills);
    }

    /** Answer-free feasibility for the exact sections that deterministic generation will include. */
    @Transactional(readOnly = true)
    public ExamDefinitionFeasibilityResponse getTemplateFeasibility(UUID templatePublicId,
            Set<String> selectedSkills) {
        return examGenerationService.getTemplateFeasibility(templatePublicId, selectedSkills);
    }

    /** Safe, answer-free readiness report used by authoring/admin screens. */
    @Transactional(readOnly = true)
    public ExamDefinitionFeasibilityResponse getTemplateFeasibility(UUID templatePublicId) {
        if (examDefinitionService == null) {
            throw new IllegalStateException(com.pte.assessment.internal.constant.ExamDefinitionConstants.TEMPLATE_FEASIBILITY_DEPENDENCIES_NOT_CONFIGURED);
        }
        return examDefinitionService.getTemplateFeasibility(templatePublicId);
    }
}
