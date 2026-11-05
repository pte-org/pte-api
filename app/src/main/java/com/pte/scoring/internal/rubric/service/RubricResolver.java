package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.TaskRubric;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the {@link EffectiveRubric} of a task type from a stored rubric version: the numbers (weights, Form bands,
 * gates, Azure mapping version) come ONLY from the version row's {@code parameters}; the catalog supplies the trait
 * structure, scales and descriptions. {@link RubricCatalog#defaultParameters} is never consulted, so changing code
 * defaults never changes the effective rubric of an existing version. The row also stores the trait scales it was
 * authored against; if the catalog's scale of a trait no longer matches (a later code change to a maximum), resolving
 * fails closed with {@link RubricConstants#RUBRIC_VERSION_SCALE_DRIFT} instead of silently rescaling the version.
 *
 * <p>Scoring jobs resolve the rubric when the job executes, not when it is queued: a worker reads the version pinned
 * on the attempt's exam definition ({@link #resolveForExamDefinition}) at that moment, and practice, which has no exam
 * definition, uses the ACTIVE version ({@link #resolveActiveVersion}). A legacy exam definition without a pin, or an
 * answer row without an exam definition, also uses the ACTIVE version (the same rule phase 2 applies when cloning a
 * template with a null pin); a pin naming a version that cannot be read fails closed instead of falling back. There
 * is no separate deployment procedure for jobs in flight.
 */
@Service
public class RubricResolver {

    private final ScoringRubricVersionRepository versionRepository;
    private final RubricCatalog catalog;
    private final RubricParametersCodec codec;
    private final ExamDefinitionService examDefinitions;

    public RubricResolver(ScoringRubricVersionRepository versionRepository, RubricCatalog catalog,
                          RubricParametersCodec codec, ExamDefinitionService examDefinitions) {
        this.versionRepository = versionRepository;
        this.catalog = catalog;
        this.codec = codec;
        this.examDefinitions = examDefinitions;
    }

    /** The rubric an exam answer is scored with: the version pinned on its exam definition, else the ACTIVE one. */
    @Transactional(readOnly = true)
    public ResolvedRubric resolveForExamDefinition(UUID examDefinitionPublicId, String taskType) {
        UUID pinned = examDefinitionPublicId == null ? null
                : examDefinitions.getByPublicId(examDefinitionPublicId).rubricVersionPublicId();
        ScoringRubricVersion version = pinned == null ? loadActive() : load(pinned);
        return new ResolvedRubric(version.getPublicId(), effectiveFor(version, taskType));
    }

    /** The rubric a practice answer is scored with: the ACTIVE version. */
    @Transactional(readOnly = true)
    public ResolvedRubric resolveActiveVersion(String taskType) {
        ScoringRubricVersion version = loadActive();
        return new ResolvedRubric(version.getPublicId(), effectiveFor(version, taskType));
    }

    /** The rubric of a pinned version (exam definitions pin {@code rubricVersionPublicId}). */
    @Transactional(readOnly = true)
    public EffectiveRubric resolve(UUID versionPublicId, String taskType) {
        return effectiveFor(load(versionPublicId), taskType);
    }

    public EffectiveRubric effectiveFor(ScoringRubricVersion version, String taskType) {
        TaskRubric task = catalog.find(taskType)
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN));
        return build(task, codec.decode(version.getParameters()));
    }

    /** Every catalog task type in catalog order, decoding the version's parameters once. */
    public Map<String, EffectiveRubric> effectiveRubrics(ScoringRubricVersion version) {
        StoredRubricParameters stored = codec.decode(version.getParameters());
        Map<String, EffectiveRubric> result = new LinkedHashMap<>();
        catalog.all().forEach(task -> result.put(task.taskType(), build(task, stored)));
        return result;
    }

    private ScoringRubricVersion load(UUID versionPublicId) {
        return versionRepository.findByPublicId(versionPublicId)
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_VERSION_NOT_FOUND));
    }

    private ScoringRubricVersion loadActive() {
        return versionRepository.findByStatus(RubricVersionStatus.ACTIVE)
                .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                        RubricConstants.RUBRIC_VERSION_NO_ACTIVE));
    }

    private static EffectiveRubric build(TaskRubric task, StoredRubricParameters stored) {
        RubricParameters parameters = stored.taskTypes().get(task.taskType());
        if (parameters == null) {
            throw new RubricVersionException(HttpStatus.INTERNAL_SERVER_ERROR,
                    RubricConstants.RUBRIC_VERSION_TASK_TYPE_MISSING);
        }
        if (!StoredRubricParameters.scalesOf(task).equals(stored.traitScales().get(task.taskType()))) {
            throw new RubricVersionException(HttpStatus.INTERNAL_SERVER_ERROR,
                    RubricConstants.RUBRIC_VERSION_SCALE_DRIFT);
        }
        return new EffectiveRubric(task, parameters);
    }
}
