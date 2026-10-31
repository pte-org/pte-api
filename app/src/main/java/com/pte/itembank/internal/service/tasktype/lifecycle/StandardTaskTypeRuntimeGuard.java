package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.dto.request.UpdateTaskTypeRequest;
import com.pte.itembank.internal.exception.tasktype.TaskTypeStandardRuntimeImmutableException;

/**
 * A standard task type's screen, contract and section are tied to its code in the client's renderer registry, so
 * changing them through the admin API would desynchronize delivery from the app. Values equal to the stored ones
 * are accepted: the admin form resends them unchanged on every save.
 */
final class StandardTaskTypeRuntimeGuard {

    private StandardTaskTypeRuntimeGuard() {
    }

    static void rejectChange(QuestionType definition, UpdateTaskTypeRequest request) {
        boolean screenChanged = request.screenKey() != null
                && !request.screenKey().equals(definition.getScreenKey());
        boolean contractChanged = request.contractVersion() != null
                && !request.contractVersion().equals(definition.getRuntimeProfileVersion());
        boolean sectionChanged = request.section() != null
                && !request.section().trim().equalsIgnoreCase(definition.getSection().name());
        if (screenChanged || contractChanged || sectionChanged) {
            throw new TaskTypeStandardRuntimeImmutableException(definition.getTaskTypeKey());
        }
    }
}
