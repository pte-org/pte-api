package com.pte.assessment.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.util.List;
import java.util.UUID;

/** Answer-stripped summary — safe for {@code session} and any human-facing endpoint. */
public record SnapshotResponse(
        UUID publicId,
        String name,
        int version,
        UUID sourceBlueprintPublicId,
        UUID scoreTemplatePublicId,
        int scoreTemplateVersion,
        UUID tenantId,
        List<Item> items) {

    public record Item(int orderIndex, String section, String taskType, String title,
            String taskTypeKey, String taskTypeDisplayName, String taskTypeCode,
            TaskRuntimeProfileDescriptor runtime,
            String runtimeMappingVersion, String runtimeMappingStatus) {

        public Item(int orderIndex, String section, String taskType, String title,
                String taskTypeCode, TaskRuntimeProfileDescriptor runtime) {
            this(orderIndex, section, taskType, title, taskTypeCode, title, taskTypeCode, runtime, null, null);
        }

        public Item(int orderIndex, String section, String taskType, String title,
                String taskTypeCode, TaskRuntimeProfileDescriptor runtime,
                String runtimeMappingVersion, String runtimeMappingStatus) {
            this(orderIndex, section, taskType, title, taskTypeCode, title, taskTypeCode,
                    runtime, runtimeMappingVersion, runtimeMappingStatus);
        }

        public Item(int orderIndex, String section, String taskType, String title) {
            this(orderIndex, section, taskType, title, taskType, title, taskType, null, null, null);
        }

    }
}
