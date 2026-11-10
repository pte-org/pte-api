package com.pte.scoring.internal.service.review;

import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.exception.InvalidAnswerStatusException;

import java.util.Locale;
import java.util.UUID;

/** Turns the raw status filter and search box of the answer list into typed query arguments. */
final class AnswerFilterParser {

    private AnswerFilterParser() {
    }

    /** An explicit status filter: blank means none, an unknown value is rejected. */
    static ScoringAnswerStatus status(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        try {
            return ScoringAnswerStatus.valueOf(statusFilter.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidAnswerStatusException();
        }
    }

    /** The search text read as a status name, or null when it is not one. */
    static ScoringAnswerStatus searchStatus(String search) {
        if (search == null) {
            return null;
        }
        try {
            return ScoringAnswerStatus.valueOf(search.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The search text read as an id, or null when it is not one. */
    static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
