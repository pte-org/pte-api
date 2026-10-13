package com.pte.overview.internal.dto.response;

import java.math.BigDecimal;

/** One calendar-month amount ({@code yyyy-MM}, Asia/Ho_Chi_Minh); {@code partial} marks the month still in progress. */
public record MonthlyAmount(String month, BigDecimal amount, boolean partial) {
}
