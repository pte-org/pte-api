package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.ExamSnapshot;
import com.pte.assessment.domain.SnapshotItem;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.EmptyBlueprintException;
import com.pte.assessment.internal.exception.SnapshotRuntimeContractException;
import com.pte.assessment.internal.mapper.SnapshotMapper;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.assessment.internal.repository.ExamSnapshotRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeObservability;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

/**
 * Freezes a blueprint into an immutable, versioned {@link ExamSnapshot} by
 * DEEP-COPYING each question's content (including options serialized to JSON,
 * already in delivery order from {@code ItembankService}) so a
 * published snapshot never changes when source questions are later edited.
 *
 * <p>No outbox/event emission (plan.md's forbidden-artifact list) — in the
 * monolith, {@code session} reads a published snapshot through
 * {@link com.pte.assessment.AssessmentService#getSummary} directly, an
 * in-process call rather than an eventually-consistent projection.
 */
@Service
public class SnapshotPublishService {

    private final ExamBlueprintRepository blueprintRepository;
    private final ExamSnapshotRepository snapshotRepository;
    private final ItembankService itembankService;
    private final AssessmentAccessPolicy accessPolicy;
    private final JsonMapper jsonMapper;
    private final ScoreTemplateService scoreTemplateService;
    private final TaskRuntimeProfileService runtimeProfileService;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;

    @Autowired
    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, ExamSnapshotRepository snapshotRepository,
                                  ItembankService itembankService, AssessmentAccessPolicy accessPolicy,
                                  JsonMapper jsonMapper, ScoreTemplateService scoreTemplateService,
                                  TaskRuntimeProfileService runtimeProfileService,
                                  AuditLogService auditLogService,
                                  TaskTypeObservability observability) {
        this.blueprintRepository = blueprintRepository;
        this.snapshotRepository = snapshotRepository;
        this.itembankService = itembankService;
        this.accessPolicy = accessPolicy;
        this.jsonMapper = jsonMapper;
        this.scoreTemplateService = scoreTemplateService;
        this.runtimeProfileService = runtimeProfileService;
        this.auditLogService = auditLogService;
        this.observability = observability;
    }

    /** Compatibility constructor for focused tests predating runtime provenance. */
    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, ExamSnapshotRepository snapshotRepository,
                                  ItembankService itembankService, AssessmentAccessPolicy accessPolicy,
                                  JsonMapper jsonMapper, ScoreTemplateService scoreTemplateService) {
        this(blueprintRepository, snapshotRepository, itembankService, accessPolicy, jsonMapper,
                scoreTemplateService, null, null, null);
    }

    /** Compatibility constructor for tests that provide the runtime service but not audit infrastructure. */
    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, ExamSnapshotRepository snapshotRepository,
                                  ItembankService itembankService, AssessmentAccessPolicy accessPolicy,
                                  JsonMapper jsonMapper, ScoreTemplateService scoreTemplateService,
                                  TaskRuntimeProfileService runtimeProfileService) {
        this(blueprintRepository, snapshotRepository, itembankService, accessPolicy, jsonMapper,
                scoreTemplateService, runtimeProfileService, null, null);
    }

    @Transactional
    public SnapshotResponse publish(UUID blueprintPublicId, CurrentUser caller) {
        return publish(blueprintPublicId, caller, null, null, null, null);
    }

    /** Publishes with an explicit template/provenance for the orchestration flow. */
    @Transactional
    public SnapshotResponse publish(UUID blueprintPublicId, CurrentUser caller,
            ScoreTemplateResponse requestedTemplate, Long generationSeed,
            String generationAlgorithmVersion, String poolPolicyFingerprint) {
        ExamBlueprint blueprint = blueprintRepository.findWithItemsByPublicId(blueprintPublicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(blueprint.getTenantId(), blueprint.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        if (blueprint.getItems().isEmpty()) {
            throw new EmptyBlueprintException();
        }
        // Resolved once per publish, before any mutation, so a missing ACTIVE
        // template (NoActiveScoreTemplateException, not caught here — it must
        // surface as a loud configuration error) leaves the blueprint DRAFT and
        // saves no snapshot (spec FR-13's immutable pin starts from a real value).
        ScoreTemplateResponse activeTemplate = requestedTemplate != null
                ? requestedTemplate : scoreTemplateService.getActive();

        int version = (int) snapshotRepository.countBySourceBlueprintPublicId(blueprintPublicId) + 1;
        ExamSnapshot snapshot = new ExamSnapshot();
        snapshot.setName(blueprint.getName());
        snapshot.setVersion(version);
        snapshot.setSourceBlueprintPublicId(blueprintPublicId);
        snapshot.setScoreTemplatePublicId(activeTemplate.publicId());
        snapshot.setScoreTemplateVersion(activeTemplate.version());
        snapshot.setGenerationSeed(generationSeed);
        snapshot.setGenerationAlgorithmVersion(generationAlgorithmVersion);
        snapshot.setPoolPolicyFingerprint(poolPolicyFingerprint);
        snapshot.setTenantId(blueprint.getTenantId());
        java.util.Map<String, ScoreTemplateItemResponse> templateItemsByTaskType = activeTemplate.items().stream()
                .collect(java.util.stream.Collectors.toMap(item -> TaskTypeCodeCompatibility
                                .normalizeTaskTypeKey(item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey()),
                        java.util.function.Function.identity(), (first, ignored) -> first));
        List<QuestionFreezeView> frozenQuestions = blueprint.getItems().stream()
                .map(item -> itembankService.freeze(item.getQuestionPublicId()))
                .toList();
        try {
            validateRuntimeProfiles(frozenQuestions, templateItemsByTaskType);
            for (int index = 0; index < blueprint.getItems().size(); index++) {
                var blueprintItem = blueprint.getItems().get(index);
                snapshot.addItem(toSnapshotItem(frozenQuestions.get(index), blueprintItem.getSection(),
                        blueprintItem.getOrderIndex(), templateItemsByTaskType));
            }
        } catch (SnapshotRuntimeContractException ex) {
            if (observability != null) {
                observability.snapshotRuntimeContractFailed();
            }
            if (auditLogService != null && caller != null) {
                auditLogService.recordFailure(caller, AssessmentConstants.AUDIT_AGGREGATE_SNAPSHOT,
                        blueprintPublicId.toString(), AssessmentConstants.AUDIT_RUNTIME_MAPPING_REJECTED,
                        ex.diagnosticMessage());
            }
            throw ex;
        }

        ExamSnapshot saved = snapshotRepository.save(snapshot);
        blueprint.setStatus(BlueprintStatus.PUBLISHED);
        blueprintRepository.save(blueprint);
        return SnapshotMapper.toResponse(saved);
    }

    /**
     * Full-fidelity content for the trusted application-call surface (called by
     * {@code attempt} at attempt-create). No {@link CurrentUser} check here —
     * per-student entitlement is gated by {@code session} before this call.
     */
    @Transactional(readOnly = true)
    public SnapshotContentResponse getContent(UUID publicId) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        return SnapshotMapper.toContentResponse(snapshot);
    }

    /**
     * Answer-stripped summary for the trusted application-call surface (called
     * by {@code session} at session-creation time). Same shape as {@link #get},
     * but no {@link CurrentUser}/tenant-visibility check — a session's
     * composition is validated against {@code session}'s own entitlement rules,
     * not assessment's per-tenant visibility.
     */
    @Transactional(readOnly = true)
    public SnapshotResponse getSummary(UUID publicId) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        return SnapshotMapper.toResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public SnapshotResponse get(UUID publicId, CurrentUser caller) {
        ExamSnapshot snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(snapshot.getTenantId(), snapshot.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        return SnapshotMapper.toResponse(snapshot);
    }

    private SnapshotItem toSnapshotItem(QuestionFreezeView question, PteSection section, int orderIndex,
            java.util.Map<String, ScoreTemplateItemResponse> templateItemsByTaskType) {
        SnapshotItem item = new SnapshotItem();
        item.setSourceQuestionPublicId(question.sourceQuestionPublicId());
        item.setPteTaskType(question.pteTaskType());
        String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                question.taskTypeKey() == null && question.pteTaskType() != null
                        ? question.pteTaskType().name() : question.taskTypeKey());
        item.setTaskTypeKey(taskTypeKey);
        item.setTaskTypeDisplayName(question.taskTypeDisplayName() == null
                ? taskTypeKey : question.taskTypeDisplayName());
        TaskRuntimeProfileDescriptor runtime = resolveRuntimeProfile(taskTypeKey, question.pteTaskType(),
                templateItemsByTaskType);
        item.pinRuntimeProfile(runtime, TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL,
                TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL);
        item.setSection(section);
        item.setOrderIndex(orderIndex);
        item.setTitle(question.title());
        item.setPromptText(question.promptText());
        item.setAudioPromptRef(question.audioPromptRef());
        item.setImagePromptRef(question.imagePromptRef());
        item.setReferenceAnswerText(question.referenceAnswerText());
        item.setCorrectAnswerText(question.correctAnswerText());
        item.setMinWordCount(question.minWordCount());
        item.setMaxWordCount(question.maxWordCount());
        item.setOptionsJson(serializeOptions(question.options()));
        return item;
    }

    private TaskRuntimeProfileDescriptor resolveRuntimeProfile(String taskTypeKey, PteTaskType legacyTaskType,
            java.util.Map<String, ScoreTemplateItemResponse> templateItemsByTaskType) {
        ScoreTemplateItemResponse templateItem = templateItemsByTaskType.get(taskTypeKey);
        TaskRuntimeProfileDescriptor runtime = templateItem == null && legacyTaskType != null && !legacyTaskType.isScored()
                ? TaskRuntimeProfileRegistry.descriptorFor(legacyTaskType.name())
                : templateItem == null && runtimeProfileService == null
                        && legacyTaskType != null ? TaskRuntimeProfileRegistry.descriptorFor(legacyTaskType.name())
                        : templateItem == null ? null : templateItem.runtime();
        if (runtime == null) {
            throw new SnapshotRuntimeContractException(
                    "Missing runtime profile for task type " + taskTypeKey + " in the active score template");
        }
        if (!taskTypeKey.equals(runtime.taskTypeCode())) {
            throw new SnapshotRuntimeContractException(
                    "Runtime profile task type " + runtime.taskTypeCode() + " does not match " + taskTypeKey);
        }
        if (TaskTypeCodeCompatibility.isStandard(taskTypeKey)
                && !TaskRuntimeProfileRegistry.isAllowlistedContract(runtime)) {
            throw new SnapshotRuntimeContractException(
                    "Runtime profile contract differs from the allowlisted profile for " + taskTypeKey);
        }
        if (!TaskTypeCodeCompatibility.isStandard(taskTypeKey)
                && (!("ACTIVE".equals(runtime.status()) || "RETIRED".equals(runtime.status()))
                || runtime.screenKey() == null || runtime.contractVersion() < 1
                || runtime.answerSchemaVersion() < 1 || runtime.scoringProfileKey() == null
                || runtime.scoringMode() == null)) {
            throw new SnapshotRuntimeContractException(
                    "Custom runtime profile is incomplete for " + taskTypeKey);
        }
        return runtime;
    }

    private void validateRuntimeProfiles(List<QuestionFreezeView> frozenQuestions,
            java.util.Map<String, ScoreTemplateItemResponse> templateItemsByTaskType) {
        if (runtimeProfileService == null) {
            return;
        }
        java.util.List<TaskRuntimeProfileDescriptor> profiles = frozenQuestions.stream()
                .map(question -> resolveRuntimeProfile(
                        question.taskTypeKey() == null && question.pteTaskType() != null
                                ? question.pteTaskType().name() : question.taskTypeKey(),
                        question.pteTaskType(), templateItemsByTaskType))
                .distinct()
                .toList();
        java.util.Set<TaskRuntimeProfileDescriptor> invalidStandardProfiles = profiles.stream()
                .filter(profile -> TaskTypeCodeCompatibility.isStandard(profile.taskTypeCode()))
                .filter(profile -> !TaskRuntimeProfileRegistry.isAllowlistedContract(profile))
                .collect(java.util.stream.Collectors.toSet());
        if (!invalidStandardProfiles.isEmpty()) {
            throw new SnapshotRuntimeContractException(
                    "One or more runtime profiles are not allowlisted or persisted consistently");
        }
    }

    private String serializeOptions(List<QuestionFreezeView.Option> options) {
        List<FrozenOption> frozen = options.stream()
                .map(o -> new FrozenOption(o.text(), o.correct(), o.orderIndex(), o.blankIndex(), o.correctGapIndex()))
                .toList();
        try {
            return jsonMapper.writeValueAsString(frozen);
        } catch (JacksonException ex) {
            throw new IllegalStateException(AssessmentConstants.SNAPSHOT_OPTIONS_SERIALIZATION_FAILED, ex);
        }
    }

    /**
     * Frozen option shape stored in {@code SnapshotItem.optionsJson}.
     * {@code blankIndex} is null except for {@code FILL_IN_THE_BLANKS_DROPDOWN}
     * options, where it groups options under their owning blank; {@code
     * correctGapIndex} is scoring-only, set only for {@code
     * FILL_IN_THE_BLANKS_DRAG_AND_DROP} correct options. {@code attempt}'s own frozen-option
     * reader only needs {@code text}/{@code orderIndex}/{@code blankIndex}
     * (unknown fields deserialize as ignored by default), so the two shapes are
     * not required to stay field-for-field identical — only {@code scoring}'s
     * own local reader (which needs {@code correct}/{@code correctGapIndex} for
     * grading) must match this shape exactly.
     */
    private record FrozenOption(String text, boolean correct, int orderIndex, Integer blankIndex, Integer correctGapIndex) {
    }
}
