package com.pte.assessment;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.service.SnapshotPublishService;
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
 * snapshot's summary, and {@code attempt} (Phase 07) pinning full content at
 * attempt-create. Blueprint CRUD and publish stay internal — only a host's
 * own authoring UI calls those, through {@code BlueprintController}/{@code
 * SnapshotController} directly.
 */
@Service
public class AssessmentService {

    private final SnapshotPublishService snapshotPublishService;
    private final TemplateService templateService;

    public AssessmentService(SnapshotPublishService snapshotPublishService, TemplateService templateService) {
        this.snapshotPublishService = snapshotPublishService;
        this.templateService = templateService;
    }

    /** Answer-stripped summary — safe for {@code session} to validate composition against. */
    public SnapshotResponse getSummary(UUID snapshotPublicId) {
        return snapshotPublishService.getSummary(snapshotPublicId);
    }

    /** Full-fidelity content including answer keys — trusted application call only, never expose to a human-facing response. */
    public SnapshotContentResponse getFullContent(UUID snapshotPublicId) {
        return snapshotPublishService.getContent(snapshotPublicId);
    }

    /** Active template structure for the in-process Phase 10 resolver. */
    public TemplateSpec getTemplateSpec(UUID templatePublicId) {
        return templateService.getTemplateSpec(templatePublicId);
    }
}
