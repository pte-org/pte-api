package com.pte.identity.internal.dto.response;

/** One broken field in a roster: {@code rowIndex} is 0-based in the request, {@code code} is a roster error code. */
public record RosterRowError(int rowIndex, String field, String code) {
}
