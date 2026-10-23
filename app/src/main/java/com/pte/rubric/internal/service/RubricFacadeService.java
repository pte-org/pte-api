package com.pte.rubric.internal.service;

import com.pte.rubric.RubricPublishedProjection;
import com.pte.rubric.RubricService;
import com.pte.rubric.RubricUnavailableException;
import com.pte.rubric.internal.exception.RubricNotFoundException;
import com.pte.rubric.internal.exception.RubricValidationException;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Adapter that keeps the public module facade separate from internal repositories. */
@Service
public class RubricFacadeService implements RubricService {

    private final RubricReadService readService;
    private final RubricCanonicalizer canonicalizer;

    public RubricFacadeService(RubricReadService readService, RubricCanonicalizer canonicalizer) {
        this.readService = readService;
        this.canonicalizer = canonicalizer;
    }

    @Override
    public RubricPublishedProjection resolvePublishedForTask(String taskTypeKey) {
        try {
            return readService.resolvePublishedForTask(taskTypeKey);
        } catch (RubricNotFoundException | RubricValidationException exception) {
            throw new RubricUnavailableException(exception);
        }
    }

    @Override
    public RubricPublishedProjection resolvePinned(UUID rubricRevisionPublicId) {
        return readService.resolvePinned(rubricRevisionPublicId);
    }

    @Override
    public String runtimeCompatibilityHashForTask(String taskTypeKey) {
        var descriptor = TaskRuntimeProfileRegistry.descriptorFor(taskTypeKey);
        return descriptor == null ? null : canonicalizer.runtimeCompatibilityHash(descriptor);
    }
}
