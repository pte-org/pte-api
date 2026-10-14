package com.pte.overview.internal.dto.response;

import java.util.List;

/** Cacheable wrapper of a monthly series (a bare list cannot be deserialised by element type). */
public record MonthlySeries(List<MonthlyPoint> points) {
}
