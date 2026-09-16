package com.pte.enrollment.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record BulkAssignStudentsResponse(List<UUID> assigned, List<UUID> alreadyInClass) {
}
