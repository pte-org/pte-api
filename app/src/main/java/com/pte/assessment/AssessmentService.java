package com.pte.assessment;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.SnapshotPublishService;
import org.springframework.stereotype.Service;

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

    public AssessmentService(SnapshotPublishService snapshotPublishService) {
        this.snapshotPublishService = snapshotPublishService;
    }

    /** Answer-stripped summary — safe for {@code session} to validate composition against. */
    public SnapshotResponse getSummary(UUID snapshotPublicId) {
        return snapshotPublishService.getSummary(snapshotPublicId);
    }

    /** Full-fidelity content including answer keys — trusted application call only, never expose to a human-facing response. */
    public SnapshotContentResponse getFullContent(UUID snapshotPublicId) {
        return snapshotPublishService.getContent(snapshotPublicId);
    }
}
