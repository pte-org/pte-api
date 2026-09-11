package com.pte.admin.dto.response;

import java.util.List;
import java.util.UUID;

public record BulkAssignStudentsResponse(List<UUID> assigned, List<UUID> alreadyInClass) {
}
