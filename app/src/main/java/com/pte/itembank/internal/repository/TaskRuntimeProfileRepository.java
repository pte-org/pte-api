package com.pte.itembank.internal.repository;

import com.pte.itembank.domain.TaskRuntimeProfile;
import com.pte.itembank.domain.enums.TaskRuntimeProfileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRuntimeProfileRepository extends JpaRepository<TaskRuntimeProfile, Long> {

    Optional<TaskRuntimeProfile> findByTaskTypeCodeAndStatusAndDeletedFalse(
            String taskTypeCode, TaskRuntimeProfileStatus status);

    Optional<TaskRuntimeProfile> findByTaskTypeCodeAndProfileVersionAndDeletedFalse(
            String taskTypeCode, int profileVersion);

    List<TaskRuntimeProfile> findAllByTaskTypeCodeInAndStatusAndDeletedFalse(
            Collection<String> taskTypeCodes, TaskRuntimeProfileStatus status);

    List<TaskRuntimeProfile> findAllByTaskTypeCodeInAndProfileVersionInAndDeletedFalse(
            Collection<String> taskTypeCodes, Collection<Integer> profileVersions);
}
