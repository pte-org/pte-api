package com.pte.practice;

import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.shared.practice.PracticeMediaReferencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Practice side of {@link PracticeMediaReferencePort}: a recording is still used while the answer of the item it was
 * uploaded for names it. A draft re-recorded after "Lưu & thoát", an upload whose answer never saved, and the drafts
 * of a discarded session leave recordings that nothing names any more.
 */
@Service
public class PracticeMediaReferenceService implements PracticeMediaReferencePort {

    private final PracticeResponseRepository itemRepository;

    public PracticeMediaReferenceService(PracticeResponseRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Set<UUID> findReferencedResponseAudio(Map<UUID, UUID> itemPublicIdByMediaPublicId) {
        if (itemPublicIdByMediaPublicId == null || itemPublicIdByMediaPublicId.isEmpty()) {
            return Set.of();
        }
        Map<UUID, String> payloadByItem = new HashMap<>();
        for (Object[] row : itemRepository.findLivePayloadsByPublicIds(
                Set.copyOf(itemPublicIdByMediaPublicId.values()))) {
            payloadByItem.put((UUID) row[0], (String) row[1]);
        }
        Set<UUID> referenced = new HashSet<>();
        itemPublicIdByMediaPublicId.forEach((mediaId, itemId) -> {
            String payload = payloadByItem.get(itemId);
            // The saved answer names the recording by its unique id, so a text match is exact and avoids parsing
            // every renderer's answer shape.
            if (payload != null && payload.contains(mediaId.toString())) {
                referenced.add(mediaId);
            }
        });
        return Set.copyOf(referenced);
    }
}
