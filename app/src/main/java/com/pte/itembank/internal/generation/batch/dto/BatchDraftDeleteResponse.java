package com.pte.itembank.internal.generation.batch.dto;

import java.util.List;
import java.util.UUID;

/**
 * Per-question outcome of a bulk draft delete. {@code remaining} counts the batch's drafts still live after the call;
 * the client repeats the call while it is above zero.
 */
public record BatchDraftDeleteResponse(int processed, int deleted, int remaining, List<Item> items) {

    /** {@code result}: deleted, skipped_not_draft, forbidden, not_found or not_in_batch; {@code code} the error code. */
    public record Item(UUID questionPublicId, String result, String code) {
    }
}
