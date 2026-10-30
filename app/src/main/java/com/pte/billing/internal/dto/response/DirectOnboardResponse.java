package com.pte.billing.internal.dto.response;

import java.util.UUID;

/** The created organization and its first host; the password is emailed, never returned. */
public record DirectOnboardResponse(UUID publicId, String code, String name, String hostUsername) {
}
