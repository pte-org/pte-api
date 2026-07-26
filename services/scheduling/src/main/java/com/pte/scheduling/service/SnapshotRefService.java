package com.pte.scheduling.service;

import com.pte.scheduling.client.AuthoringClient;
import com.pte.scheduling.client.dto.AuthoringSnapshotResponse;
import com.pte.scheduling.domain.SnapshotRef;
import com.pte.scheduling.domain.SnapshotRefItem;
import com.pte.scheduling.domain.exception.SnapshotFetchFailedException;
import com.pte.scheduling.repository.SnapshotRefRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Resolves a snapshot's composition metadata for session creation: cache hit
 * first, guarded sync pull to authoring on miss. Since Phase 6, the happy path
 * is event-driven instead — {@code SnapshotEventConsumer} proactively upserts
 * via {@link #upsert} as soon as authoring publishes, so most sessions never
 * reach {@link #fetchAndCache} at all; the sync pull remains as a resilient
 * fallback (cache miss, consumer lag, or a snapshot published before the
 * backbone existed).
 */
@Service
public class SnapshotRefService {

    private final SnapshotRefRepository snapshotRefRepository;
    private final AuthoringClient authoringClient;

    public SnapshotRefService(SnapshotRefRepository snapshotRefRepository, AuthoringClient authoringClient) {
        this.snapshotRefRepository = snapshotRefRepository;
        this.authoringClient = authoringClient;
    }

    @Transactional
    public SnapshotRef resolve(UUID snapshotPublicId) {
        return snapshotRefRepository.findWithItemsBySnapshotPublicId(snapshotPublicId)
                .orElseGet(() -> fetchAndCache(snapshotPublicId));
    }

    /** Idempotent upsert: replaces an existing ref's items (a republish is a new version, not a mutation in place — see composition-drift note in ADR-002). */
    @Transactional
    public SnapshotRef upsert(UUID snapshotPublicId, int version, String name, UUID tenantId, List<SnapshotItemSpec> items) {
        SnapshotRef ref = snapshotRefRepository.findWithItemsBySnapshotPublicId(snapshotPublicId)
                .orElseGet(SnapshotRef::new);
        ref.setSnapshotPublicId(snapshotPublicId);
        ref.setVersion(version);
        ref.setName(name);
        ref.setTenantId(tenantId);
        ref.getItems().clear();
        items.forEach(source -> {
            SnapshotRefItem item = new SnapshotRefItem();
            item.setTaskType(source.taskType());
            item.setSection(source.section());
            item.setOrderIndex(source.orderIndex());
            ref.addItem(item);
        });
        return snapshotRefRepository.save(ref);
    }

    private SnapshotRef fetchAndCache(UUID snapshotPublicId) {
        AuthoringSnapshotResponse remote = authoringClient.fetchSnapshot(snapshotPublicId);
        if (remote == null) {
            throw new SnapshotFetchFailedException();
        }
        List<SnapshotItemSpec> items = remote.items().stream()
                .map(source -> new SnapshotItemSpec(source.orderIndex(), source.taskType(), source.section()))
                .toList();
        return upsert(remote.publicId(), remote.version(), remote.name(), remote.tenantId(), items);
    }
}
