package com.pte.scheduling.dto.response;

import java.util.List;
import java.util.UUID;

public record BulkEnrollResponse(List<UUID> enrolled, List<UUID> alreadyEnrolled) {
}
