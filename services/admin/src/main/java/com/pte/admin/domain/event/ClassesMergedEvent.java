package com.pte.admin.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Summary payload for {@code ClassesMerged} — one per merge call. Each moved
 * student's membership update also writes its own individual
 * {@code StudentTransferredClass} event ({@link StudentTransferredClassEvent}),
 * same "N individual events + 1 summary" shape as {@link ClassSplitEvent}.
 */
public record ClassesMergedEvent(UUID targetClassPublicId, List<UUID> sourceClassPublicIds,
                                  List<UUID> movedStudentPublicIds, UUID tenantPublicId) {
}
