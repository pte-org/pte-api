package com.pte.admin.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Summary payload for {@code ClassSplit} — one per split call. Each moved
 * student's membership update also writes its own individual
 * {@code StudentTransferredClass} event ({@link StudentTransferredClassEvent}),
 * same "N individual events + 1 summary" shape as {@link ClassesMergedEvent}.
 */
public record ClassSplitEvent(UUID sourceClassPublicId, UUID newClassPublicId, List<UUID> movedStudentPublicIds,
                               UUID tenantPublicId) {
}
