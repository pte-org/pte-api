package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.TaskTypeCodeCompatibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;

/**
 * Manages runtime locking of task types after their first publication.
 */
@Service
public class TaskTypeRuntimeLockingService {

    private final QuestionTypeRepository repository;

    public TaskTypeRuntimeLockingService(QuestionTypeRepository repository) {
        this.repository = repository;
    }

    /**
     * Record the first publication of a set of task types. After the first
     * publication, the runtime binding (contract) can no longer change.
     */
    @Transactional
    public void recordPublishedUse(Collection<String> taskTypeKeys, Instant publishedAt) {
        for (String rawKey : taskTypeKeys) {
            repository.findByTaskTypeKey(TaskTypeCodeCompatibility.normalizeTaskTypeKey(rawKey))
                    .ifPresent(type -> {
                        if (type.getFirstPublishedAt() == null) {
                            type.setFirstPublishedAt(publishedAt);
                        }
                        if (type.getRuntimeLockedAt() == null) {
                            type.setRuntimeLockedAt(publishedAt);
                        }
                    });
        }
    }
}
