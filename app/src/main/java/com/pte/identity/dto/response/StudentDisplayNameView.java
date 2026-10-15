package com.pte.identity.dto.response;

import java.util.UUID;

/**
 * Minimal projection of student display name and email for proctor roster presentation.
 */
public record StudentDisplayNameView(
        UUID studentPublicId,
        String displayName,
        String email
) {
}
