package com.pte.proctor.domain.event;

import com.pte.proctor.domain.enums.ProctorCommandType;

import java.util.UUID;

/**
 * Outbox payload for {@code outbox.event.ProctorCommand}. {@code extraSeconds}
 * is only meaningful for {@code EXTEND_TIME} (null for {@code FORCE_SUBMIT}).
 */
public record ProctorCommandPublished(UUID attemptPublicId, UUID sessionPublicId, ProctorCommandType commandType,
                                       Integer extraSeconds, UUID tenantId) {
}
