package com.pte.support.internal.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a ticket's linked entity is, so a reader can tell which one was reported without opening it.
 * Only questions are described today; {@code promptSnippet} is null when the question has no text prompt.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketEntityContext(
        String taskTypeKey,
        String section,
        String promptSnippet) {
}
