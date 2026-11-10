package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * The parameters of a pinned rubric version for the task types of one attempt. Only ACTIVE or RETIRED versions are
 * ever returned; a draft, pending or rejected version is never exposed to a Host or Examiner.
 */
public record RubricParametersView(
        UUID versionPublicId,
        int versionNo,
        String status,
        int catalogVersion,
        List<TaskTypeParametersView> taskTypes) {

    public RubricParametersView {
        taskTypes = List.copyOf(taskTypes);
    }
}
