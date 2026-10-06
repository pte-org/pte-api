package com.pte.support.internal.dto.request;

import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketEntityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitTicketRequest(
        @NotNull TicketCategory category,
        @NotNull @Size(min = 1, max = 2000) String description,
        TicketEntityType entityType,
        @Size(max = 36) String entityId
) {
}
