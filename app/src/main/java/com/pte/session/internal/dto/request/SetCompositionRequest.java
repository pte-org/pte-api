package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Host selects which task types to include — the full-mock vs practice-subset mechanism. */
public record SetCompositionRequest(
        @NotEmpty(message = SessionConstants.COMPOSITION_ITEMS_REQUIRED)
        @Valid List<CompositionItemRequest> items) {
}
