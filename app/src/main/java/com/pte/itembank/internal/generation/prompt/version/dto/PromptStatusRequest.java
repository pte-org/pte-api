package com.pte.itembank.internal.generation.prompt.version.dto;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One step of the approval flow; a reason is required to reject. */
public record PromptStatusRequest(@NotNull Action action,
                                  @Size(max = PromptVersionConstants.MAX_REASON_LENGTH) String reason) {

    public enum Action {
        /** Author sends a tested draft for approval. */
        SUBMIT,
        /** A manager or admin approves; approval makes the version the live one. */
        APPROVE,
        /** A manager or admin sends it back with a reason. */
        REJECT,
        /** The author drops a draft or a rejected version. */
        DISCARD
    }
}
