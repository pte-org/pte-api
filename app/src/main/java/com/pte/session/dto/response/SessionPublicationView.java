package com.pte.session.dto.response;

import java.time.Instant;
import java.util.UUID;

/** The report publication that locked a session's scores. */
public record SessionPublicationView(UUID sessionPublicId, UUID publicationPublicId, Instant lockedAt) {
}
