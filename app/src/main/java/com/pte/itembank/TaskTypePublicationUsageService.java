package com.pte.itembank;

import com.pte.itembank.domain.TaskTypePublicationUsage;
import com.pte.itembank.internal.repository.TaskTypePublicationUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Itembank-owned publication lock boundary used by scoretemplate activation. */
@Service
public class TaskTypePublicationUsageService {

    private final TaskTypePublicationUsageRepository repository;

    @Autowired
    public TaskTypePublicationUsageService(TaskTypePublicationUsageRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isRuntimeLocked(String taskTypeKey) {
        return repository.existsByTaskTypeKey(TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey));
    }

    @Transactional(readOnly = true)
    public long publishedTemplateCount(String taskTypeKey) {
        return repository.countByTaskTypeKey(TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskTypeKey));
    }

    @Transactional
    public void recordPublishedUse(Collection<String> taskTypeKeys, UUID templatePublicId,
            UUID templateVersionPublicId, UUID actorPublicId, String auditReference) {
        if (taskTypeKeys == null || taskTypeKeys.isEmpty()) {
            return;
        }
        for (String rawKey : taskTypeKeys) {
            String key = TaskTypeCodeCompatibility.normalizeTaskTypeKey(rawKey);
            if (repository.existsByTaskTypeKeyAndTemplateVersionPublicId(key, templateVersionPublicId)) {
                continue;
            }
            TaskTypePublicationUsage usage = new TaskTypePublicationUsage();
            usage.setTaskTypeKey(key);
            usage.setTemplatePublicId(templatePublicId);
            usage.setTemplateVersionPublicId(templateVersionPublicId);
            usage.setPublishedAt(Instant.now());
            usage.setActorPublicId(actorPublicId);
            usage.setAuditReference(auditReference);
            repository.save(usage);
        }
    }
}
