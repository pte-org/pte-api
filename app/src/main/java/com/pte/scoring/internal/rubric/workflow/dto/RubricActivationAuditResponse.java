package com.pte.scoring.internal.rubric.workflow.dto;

import java.time.Instant;
import java.util.UUID;

public record RubricActivationAuditResponse(UUID publicId, UUID versionPublicId, UUID previousActivePublicId,
        UUID actorUserPublicId, Instant activatedAt, String diffSummary) { }
