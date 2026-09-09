package com.pte.proctor.domain.event;

import com.pte.proctor.domain.enums.ProctorCommandType;

import java.util.UUID;

/** Outbox payload for {@code outbox.event.ProctorCommand}. */
public record ProctorCommandPublished(UUID attemptPublicId, UUID sessionPublicId, ProctorCommandType commandType,
                                       UUID tenantId) {
}
