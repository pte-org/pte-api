package com.pte.shared.web;

import java.util.List;

/** Common page envelope nested inside {@link ApiResponse#data}. */
public record PagedResult<T>(List<T> data, PageMeta meta) {
}
