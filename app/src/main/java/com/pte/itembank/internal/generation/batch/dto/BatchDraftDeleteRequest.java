package com.pte.itembank.internal.generation.batch.dto;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /batches/{id}/drafts/delete} body. Empty or absent ids mean "the batch's drafts" (up to the limit per
 * call); given ids are only touched when they belong to the batch.
 */
public record BatchDraftDeleteRequest(
        @Size(max = BatchGenerationConstants.MAX_BULK_DELETE_ITEMS, message = BatchGenerationConstants.MAX_BULK_MESSAGE)
        List<@NotNull UUID> questionPublicIds) {

    public List<UUID> idsOrEmpty() {
        return questionPublicIds == null ? List.of() : questionPublicIds;
    }
}
