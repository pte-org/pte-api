package com.pte.session.internal.dto.response.proctor;

public record ProctorOverviewCountsResponse(
        long accessible,
        long upcoming,
        long today
) {
}
