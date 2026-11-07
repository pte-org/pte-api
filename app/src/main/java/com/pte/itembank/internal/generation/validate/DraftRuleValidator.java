package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.draft.ItemDraft;

import java.util.List;

/** One group of structural rules for a draft shape; the {@link DraftValidator} facade picks the right one. */
public interface DraftRuleValidator {

    /** The rule violations of {@code draft}; empty when it passes. */
    List<ValidationIssue> validate(ItemDraft draft);
}
