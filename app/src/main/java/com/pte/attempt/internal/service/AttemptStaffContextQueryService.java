package com.pte.attempt.internal.service;

import com.pte.attempt.dto.response.AttemptStaffContextView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded scalar reads owned by Attempt; missing/deleted references are omitted. */
@Service
public class AttemptStaffContextQueryService {
    private final ExamAttemptRepository attempts;
    public AttemptStaffContextQueryService(ExamAttemptRepository attempts) { this.attempts = attempts; }

    @Transactional(readOnly = true)
    public Map<UUID, AttemptStaffContextView> find(UUID tenantId, Collection<UUID> ids) {
        var selected = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (selected.isEmpty()) { return Map.of(); }
        if (selected.size() > 100) { throw new IllegalArgumentException("STAFF_CONTEXT_BATCH_TOO_LARGE"); }
        var tasks = new LinkedHashMap<UUID, Map<String, Long>>();
        for (var row : attempts.countStaffPinnedTasks(tenantId, selected)) {
            if (row.getTaskType() != null) {
                tasks.computeIfAbsent(row.getAttemptPublicId(), key -> new LinkedHashMap<>())
                        .merge(row.getTaskType(), row.getTaskCount(), Long::sum);
            }
        }
        var result = new LinkedHashMap<UUID, AttemptStaffContextView>();
        for (var row : attempts.findStaffContexts(tenantId, selected)) {
            boolean valid = row.getPinnedPublicId() != null && row.getScoreTemplatePublicId() != null
                    && tenantId.equals(row.getPinnedTenantId())
                    && row.getSessionPublicId().equals(row.getPinnedSessionPublicId())
                    && !tasks.getOrDefault(row.getAttemptPublicId(), Map.of()).isEmpty();
            result.put(row.getAttemptPublicId(), new AttemptStaffContextView(row.getAttemptPublicId(),
                    row.getSessionPublicId(), row.getScoreTemplatePublicId(), valid,
                    tasks.getOrDefault(row.getAttemptPublicId(), Map.of())));
        }
        return Map.copyOf(result);
    }
}
