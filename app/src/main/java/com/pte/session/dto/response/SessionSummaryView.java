package com.pte.session.dto.response;

import java.util.UUID;

/** Minimal session context used by cross-module student activity projections. */
public record SessionSummaryView(
        UUID publicId,
        String name,
        String sessionCode,
        String status) {
}
