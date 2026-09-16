package com.pte.session.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record BulkEnrollResponse(List<UUID> enrolled, List<UUID> alreadyEnrolled) {
}
