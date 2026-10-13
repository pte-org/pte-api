package com.pte.overview.internal.dto.response;

/** One calendar-month bucket ({@code yyyy-MM}, Asia/Ho_Chi_Minh); {@code partial} marks the month still in progress. */
public record MonthlyPoint(String month, long count, boolean partial) {
}
