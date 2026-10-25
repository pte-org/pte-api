package com.pte.attempt.internal.service;

import com.pte.attempt.dto.response.AttemptStaffContextView;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded scalar reads owned by Attempt; missing/deleted references are omitted. */
@Service
public class AttemptStaffContextQueryService {
    private final ExamAttemptRepository attempts;
    private final ExamContentService contentService;

    public AttemptStaffContextQueryService(ExamAttemptRepository attempts, ExamContentService contentService) {
        this.attempts = attempts;
        this.contentService = contentService;
    }

    @Transactional(readOnly = true)
    public Map<UUID, AttemptStaffContextView> find(UUID tenantId, Collection<UUID> ids) {
        var selected = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (selected.isEmpty()) { return Map.of(); }
        if (selected.size() > 100) { throw new IllegalArgumentException("STAFF_CONTEXT_BATCH_TOO_LARGE"); }
        var result = new LinkedHashMap<UUID, AttemptStaffContextView>();
        for (var row : attempts.findStaffContexts(tenantId, selected)) {
            Map<String, Long> tasks = new LinkedHashMap<>();
            Map<String, TaskRuntimeProfileDescriptor> runtimes = new LinkedHashMap<>();
            if (row.getExamVersionPublicId() != null) {
                for (ExamContentService.ExamItemOutline item : contentService.outline(row.getExamVersionPublicId())) {
                    if (item.taskType() != null) {
                        tasks.merge(item.taskType(), 1L, Long::sum);
                        if (item.runtime() != null) {
                            runtimes.putIfAbsent(item.taskType(), item.runtime());
                        }
                    }
                }
            }
            boolean valid = row.getExamDefinitionPublicId() != null && !tasks.isEmpty();
            result.put(row.getAttemptPublicId(), new AttemptStaffContextView(row.getAttemptPublicId(),
                    row.getSessionPublicId(), row.getExamDefinitionPublicId(), valid, tasks, runtimes));
        }
        return Map.copyOf(result);
    }
}
