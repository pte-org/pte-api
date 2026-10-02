package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.TaskTypePublicationUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.UUID;

public interface TaskTypePublicationUsageRepository extends JpaRepository<TaskTypePublicationUsage, Long> {

    boolean existsByTaskTypeKey(String taskTypeKey);

    long countByTaskTypeKey(String taskTypeKey);

    @Query("select count(u) > 0 from TaskTypePublicationUsage u "
            + "where u.taskTypeKey in :taskTypeKeys")
    boolean existsByTaskTypeKeyIn(Collection<String> taskTypeKeys);

    boolean existsByTaskTypeKeyAndTemplateVersionPublicId(String taskTypeKey, UUID templateVersionPublicId);
}
