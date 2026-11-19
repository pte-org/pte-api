package com.pte.itembank.internal.generation.dto;

/** Which daily limit binds the caller: their own, or the one shared by all authors. */
public enum QuotaScope {
    USER,
    GLOBAL
}
