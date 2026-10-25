package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.ExamVersion;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.exception.EmptyBlueprintException;
import com.pte.assessment.internal.exception.SnapshotRuntimeContractException;
import com.pte.assessment.internal.mapper.SnapshotMapper;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.assessment.internal.repository.ExamVersionRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeObservability;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
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
    private final ExamVersionRepository snapshotRepository;
    private final ItembankService itembankService;
    private final AssessmentAccessPolicy accessPolicy;
    private final ExamDefinitionService examDefinitionService;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;
    private final SnapshotItemFactory itemFactory;
    private final SnapshotRuntimeProfileResolver profileResolver;

    @Autowired
    public SnapshotPublishService(ExamBlueprintRepository blueprintRepository, ExamVersionRepository snapshotRepository,
                                  ItembankService itembankService, AssessmentAccessPolicy accessPolicy,
                                  JsonMapper jsonMapper, ExamDefinitionService examDefinitionService,
                                  TaskRuntimeProfileService runtimeProfileService,
                                  AuditLogService auditLogService,
                                  TaskTypeObservability observability) {
        this.blueprintRepository = blueprintRepository;
        this.snapshotRepository = snapshotRepository;
        this.itembankService = itembankService;
        this.accessPolicy = accessPolicy;
        this.examDefinitionService = examDefinitionService;
        this.auditLogService = auditLogService;
        this.observability = observability;
        this.profileResolver = new SnapshotRuntimeProfileResolver(runtimeProfileService);
        this.itemFactory = new SnapshotItemFactory(jsonMapper, profileResolver);
    }

    @Transactional
    public SnapshotResponse publish(UUID blueprintPublicId, CurrentUser caller) {
        return publish(blueprintPublicId, caller, null, null, null, null);
    }

    /** Publishes with an explicit template/provenance for the orchestration flow. */
    @Transactional
    public SnapshotResponse publish(UUID blueprintPublicId, CurrentUser caller,
            ExamDefinitionResponse requestedTemplate, Long generationSeed,
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
        ExamDefinitionResponse activeTemplate = requestedTemplate != null
                ? requestedTemplate : examDefinitionService.getActive();

        int version = (int) snapshotRepository.countBySourceBlueprintPublicId(blueprintPublicId) + 1;
        ExamVersion snapshot = new ExamVersion();
        snapshot.setName(blueprint.getName());
        snapshot.setVersion(version);
        snapshot.setSourceBlueprintPublicId(blueprintPublicId);
        snapshot.setExamDefinitionPublicId(activeTemplate.publicId());
        snapshot.setExamDefinitionVersion(activeTemplate.version());
        snapshot.setGenerationSeed(generationSeed);
        snapshot.setGenerationAlgorithmVersion(generationAlgorithmVersion);
        snapshot.setPoolPolicyFingerprint(poolPolicyFingerprint);
        snapshot.setTenantId(blueprint.getTenantId());
        java.util.Map<String, ExamDefinitionItemResponse> templateItemsByTaskType = activeTemplate.items().stream()
                .collect(java.util.stream.Collectors.toMap(item -> TaskTypeCodeCompatibility
                                .normalizeTaskTypeKey(item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey()),
                        java.util.function.Function.identity(), (first, ignored) -> first));
        List<QuestionFreezeView> frozenQuestions = blueprint.getItems().stream()
                .map(item -> itembankService.freeze(item.getQuestionPublicId()))
                .toList();
        try {
            profileResolver.validateRuntimeProfiles(frozenQuestions, templateItemsByTaskType);
            for (int index = 0; index < blueprint.getItems().size(); index++) {
                var blueprintItem = blueprint.getItems().get(index);
                snapshot.addItem(itemFactory.toSnapshotItem(frozenQuestions.get(index), blueprintItem.getSection(),
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

        ExamVersion saved = snapshotRepository.save(snapshot);
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
        ExamVersion snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
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
        ExamVersion snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        return SnapshotMapper.toResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public SnapshotResponse get(UUID publicId, CurrentUser caller) {
        ExamVersion snapshot = snapshotRepository.findWithItemsByPublicId(publicId)
                .orElseThrow(BlueprintNotFoundException::new);
        if (!accessPolicy.canRead(snapshot.getTenantId(), snapshot.getTenantId() == null, caller)) {
            throw new BlueprintNotFoundException();
        }
        return SnapshotMapper.toResponse(snapshot);
    }

}
