package com.pte.rubric;

import java.util.UUID;

/** Public module facade for immutable rubric reads. */
public interface RubricService {

    RubricPublishedProjection resolvePublishedForTask(String taskTypeKey);

    RubricPublishedProjection resolvePinned(UUID rubricRevisionPublicId);

    String runtimeCompatibilityHashForTask(String taskTypeKey);
}
