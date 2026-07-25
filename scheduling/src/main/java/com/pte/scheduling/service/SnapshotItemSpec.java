package com.pte.scheduling.service;

/**
 * Common shape both {@link SnapshotRefService}'s sync-pull path and
 * {@code SnapshotEventConsumer}'s event-driven path (Phase 6) map into before
 * upserting a {@link com.pte.scheduling.domain.SnapshotRef} — avoids duplicating
 * the upsert logic across the two ingestion paths.
 */
public record SnapshotItemSpec(int orderIndex, String taskType, String section) {
}
