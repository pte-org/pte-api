package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.ProctorRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProctorRoleRequest(@NotNull(message = "Role is required") ProctorRole role) {
}
