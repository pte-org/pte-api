package com.pte.assessment;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.ExamGenerationService;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code assessment}. {@code
 * SnapshotPublishService}, repositories, and controllers stay in
 * {@code internal/}.
 *
 * <p>Starts with the two read methods known to have cross-module callers:
 * {@code session} (Phase 06) validating composition against a published
 * snapshot's summary, and {@code attempt} (Phase 07) pinning full content at
 * attempt-create. Blueprint CRUD and publish stay internal — only a host's
 * own authoring UI calls those, through {@code BlueprintController}/{@code
 * SnapshotController} directly.
 */
@Service
public class AssessmentService {

    private final SnapshotPublishService snapshotPublishService;
    private final ExamGenerationService examGenerationService;

    public AssessmentService(SnapshotPublishService snapshotPublishService, ExamGenerationService examGenerationService) {
        this.snapshotPublishService = snapshotPublishService;
        this.examGenerationService = examGenerationService;
    }

    /** Answer-stripped summary — safe for {@code session} to validate composition against. */
    public SnapshotResponse getSummary(UUID snapshotPublicId) {
        return snapshotPublishService.getSummary(snapshotPublicId);
    }

    /** Full-fidelity content including answer keys — trusted application call only, never expose to a human-facing response. */
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
}
