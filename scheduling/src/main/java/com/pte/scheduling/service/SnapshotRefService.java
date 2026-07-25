package com.pte.scheduling.service;

import com.pte.scheduling.client.AuthoringClient;
import com.pte.scheduling.client.dto.AuthoringSnapshotResponse;
import com.pte.scheduling.domain.SnapshotRef;
import com.pte.scheduling.domain.SnapshotRefItem;
import com.pte.scheduling.domain.exception.SnapshotFetchFailedException;
import com.pte.scheduling.repository.SnapshotRefRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves a snapshot's composition metadata for session creation: cache hit
 * first, guarded sync pull to authoring on miss. This is what keeps the create
 * path off authoring's runtime path after the first session against a given
 * snapshot.
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

    private SnapshotRef fetchAndCache(UUID snapshotPublicId) {
        AuthoringSnapshotResponse remote = authoringClient.fetchSnapshot(snapshotPublicId);
        if (remote == null) {
            throw new SnapshotFetchFailedException();
        }
        SnapshotRef ref = new SnapshotRef();
        ref.setSnapshotPublicId(remote.publicId());
        ref.setVersion(remote.version());
        ref.setName(remote.name());
        ref.setTenantId(remote.tenantId());
        remote.items().forEach(source -> {
            SnapshotRefItem item = new SnapshotRefItem();
            item.setTaskType(source.taskType());
            item.setSection(source.section());
            item.setOrderIndex(source.orderIndex());
            ref.addItem(item);
        });
        return snapshotRefRepository.save(ref);
    }
}
