package com.pte.session.internal.dto.response;

import java.util.UUID;

/** Result of exchanging a student-typed exam code for the session it names. */
public record SessionResolveResponse(UUID sessionPublicId) {
}
