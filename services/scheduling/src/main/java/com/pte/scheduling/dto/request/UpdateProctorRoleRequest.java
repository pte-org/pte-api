package com.pte.scheduling.dto.request;

import com.pte.scheduling.domain.enums.ProctorRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProctorRoleRequest(@NotNull(message = "Role is required") ProctorRole role) {
}
