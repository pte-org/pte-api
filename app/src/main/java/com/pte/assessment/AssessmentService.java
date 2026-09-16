package com.pte.assessment;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.dto.response.SnapshotScoringSpec;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.assessment.internal.service.TemplateResolverService;
import com.pte.assessment.internal.service.TemplateService;
import org.springframework.stereotype.Service;

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
    private final TemplateService templateService;
    private final TemplateResolverService templateResolverService;

    public AssessmentService(SnapshotPublishService snapshotPublishService, TemplateService templateService,
                             TemplateResolverService templateResolverService) {
        this.snapshotPublishService = snapshotPublishService;
        this.templateService = templateService;
        this.templateResolverService = templateResolverService;
    }

    /** Answer-stripped summary - safe for {@code session} to validate composition against. */
    public SnapshotResponse getSummary(UUID snapshotPublicId) {
        return snapshotPublishService.getSummary(snapshotPublicId);
    }

    /** Full-fidelity content including answer keys - trusted application call only. */
    public SnapshotContentResponse getFullContent(UUID snapshotPublicId) {
        return snapshotPublishService.getContent(snapshotPublicId);
    }

    /** Active template structure for the in-process Phase 10 resolver. */
    public TemplateSpec getTemplateSpec(UUID templatePublicId) {
        return templateService.getTemplateSpec(templatePublicId);
    }

    /** Generates and publishes a deterministic snapshot from an active template. */
    public SnapshotResponse generateSnapshotFromTemplate(UUID templatePublicId, long seed) {
        return templateResolverService.resolve(templatePublicId, seed);
    }

    /** Safe reporting contract containing only the weights captured in a snapshot. */
    public SnapshotScoringSpec getSnapshotScoringSpec(UUID snapshotPublicId) {
        return snapshotPublishService.getScoringSpec(snapshotPublicId);
    }
}
