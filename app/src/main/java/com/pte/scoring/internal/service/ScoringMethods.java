package com.pte.scoring.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.scoring.domain.enums.ScoringMethod;

import java.util.Optional;

/**
 * How an exam item is scored, read from the runtime contract frozen on the item itself. There is no lookup in a
 * mutable catalog or in the exam definition: an item without a usable contract simply has no method.
 */
final class ScoringMethods {

    private ScoringMethods() {
    }

    static Optional<ScoringMethod> of(TaskRuntimeProfileDescriptor runtime) {
        return runtime == null ? Optional.empty() : ScoringProfileRegistry.resolve(runtime);
    }

    static boolean isAiScored(TaskRuntimeProfileDescriptor runtime) {
        return of(runtime).map(ScoringMethod::isAiScored).orElse(false);
    }
}
