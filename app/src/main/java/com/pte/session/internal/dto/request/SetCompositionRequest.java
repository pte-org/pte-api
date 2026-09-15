package com.pte.session.internal.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Host selects which task types to include — the full-mock vs practice-subset mechanism. */
public record SetCompositionRequest(
        @NotEmpty(message = "Composition needs at least one item")
        @Valid List<CompositionItemRequest> items) {
}
