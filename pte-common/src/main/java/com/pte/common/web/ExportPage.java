package com.pte.common.web;

import java.util.List;

/**
 * Generic keyset-paginated export envelope for every {@code /internal/**}
 * export endpoint (rabbitmq-outbox-migration Phase 9, reporting read-model
 * rebuild) — identical shape across every producing service so reporting's
 * rebuild client is uniform, not N bespoke integrations.
 *
 * @param nextCursor opaque cursor (see {@link KeysetCursor}) to pass as
 *                    {@code since} on the next page request; meaningless once
 *                    {@code hasMore} is false
 */
public record ExportPage<T>(List<T> items, String nextCursor, boolean hasMore) {
}
