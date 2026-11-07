package com.pte.itembank.internal.generation.validate;

/**
 * One validation failure. Code is machine-readable; message is for human display.
 */
public record ValidationIssue(String code, String message) {
}
