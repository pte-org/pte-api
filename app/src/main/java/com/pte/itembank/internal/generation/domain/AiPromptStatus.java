package com.pte.itembank.internal.generation.domain;

/** DRAFT -> PENDING -> ACTIVE (approval activates) or REJECTED; the previous ACTIVE becomes RETIRED. */
public enum AiPromptStatus {
    DRAFT, PENDING, ACTIVE, REJECTED, RETIRED
}
