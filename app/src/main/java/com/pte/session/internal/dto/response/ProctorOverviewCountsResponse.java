package com.pte.session.internal.dto.response;

public record ProctorOverviewCountsResponse(
        long accessible,
        long upcoming,
        long today
) {
}
