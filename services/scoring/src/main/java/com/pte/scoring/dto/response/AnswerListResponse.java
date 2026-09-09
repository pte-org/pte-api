package com.pte.scoring.dto.response;

import java.util.List;

/**
 * Offset-paginated wrapper (Spring Data {@code Pageable}/{@code Page}) —
 * deliberately not the keyset-cursor shape {@code ExportPage} uses for the
 * internal cross-tenant export: that pattern exists there to page reliably
 * through a large historical dataset for a system-to-system rebuild; this is
 * an interactive host review screen over one tenant's data, where Spring's
 * built-in offset paging is simpler and sufficient.
 */
public record AnswerListResponse(List<AnswerListItemResponse> items, int page, int size, long totalElements,
                                  int totalPages) {
}
