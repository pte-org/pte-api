package com.pte.assessment;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.itembank.ItembankService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionSlotFeasibilityResponse;
import com.pte.assessment.internal.exception.NoActiveExamDefinitionException;
import com.pte.assessment.internal.exception.ExamDefinitionNotFoundException;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The only door other modules use to reach {@code assessment}. {@code
 * ScoreTemplateRepository}, the admin service, and the controller stay in
 * {@code internal/}. Read-only and unauthenticated by design — callers
 * ({@code assessment} pinning at publish, {@code attempt} for timing,
 * {@code scoring} for scoringMethod, {@code reporting} for weights) are
 * trusted in-process application code, not end users; PLATFORM_ADMIN write
 * access is enforced only at {@code ScoreTemplateController}.
 */
@Service
public class ExamDefinitionService {

    private final ExamDefinitionRepository repository;
    private final ItembankService itembankService;
    private final TaskRuntimeProfileService runtimeProfileService;

    @Autowired
    public ExamDefinitionService(ExamDefinitionRepository repository, ItembankService itembankService,
            TaskRuntimeProfileService runtimeProfileService) {
        this.repository = repository;
        this.itembankService = itembankService;
        this.runtimeProfileService = runtimeProfileService;
    }

    /** Used by {@code assessment.SnapshotPublishService} to pin the scoring scheme at exam-publish time. */
    @Transactional(readOnly = true)
    public ExamDefinitionResponse getActive() {
        ExamDefinition active = repository.findWithItemsByStatus(ExamDefinitionStatus.ACTIVE)
                .orElseThrow(NoActiveExamDefinitionException::new);
        return ExamDefinitionMapper.toResponse(active);
    }

    /**
     * Used by everything that already has a pinned {@code scoreTemplatePublicId}
     * (attempt/scoring/reporting) — deliberately does NOT filter by status, so
     * a snapshot pinned to a now-RETIRED template still resolves (immutability
     * guarantee: a published exam's scoring never drifts).
     */
    @Transactional(readOnly = true)
    public ExamDefinitionResponse getByPublicId(UUID publicId) {
        ExamDefinition template = repository.findWithItemsByPublicId(publicId)
                .orElseThrow(ExamDefinitionNotFoundException::new);
        return ExamDefinitionMapper.toResponse(template);
    }

    /** Resolves pinned templates for a report cohort with one item-fetch query. */
    @Transactional(readOnly = true)
    public Map<UUID, ExamDefinitionResponse> getByPublicIds(Collection<UUID> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(publicIds);
        Map<UUID, ExamDefinitionResponse> templates = repository.findAllWithItemsByPublicIds(List.copyOf(uniqueIds))
                .stream()
                .map(ExamDefinitionMapper::toResponse)
                .collect(java.util.stream.Collectors.toMap(ExamDefinitionResponse::publicId, template -> template));
        if (templates.size() != uniqueIds.size()) {
            throw new ExamDefinitionNotFoundException();
        }
        return Map.copyOf(templates);
    }

    /** Returns a template only when it is currently ACTIVE, without exposing its internal enum. */
    @Transactional(readOnly = true)
    public Optional<ExamDefinitionResponse> findActiveByPublicId(UUID publicId) {
        return repository.findWithItemsByPublicId(publicId)
                .filter(template -> template.getStatus() == ExamDefinitionStatus.ACTIVE)
                .map(ExamDefinitionMapper::toResponse);
    }

    /** Answer-free catalog readiness report used by authoring and exam orchestration. */
    @Transactional(readOnly = true)
    public ExamDefinitionFeasibilityResponse getTemplateFeasibility(UUID templatePublicId) {
        if (itembankService == null) {
            throw new IllegalStateException(ExamDefinitionConstants.TEMPLATE_FEASIBILITY_DEPENDENCIES_NOT_CONFIGURED);
        }
        ExamDefinitionResponse template = getByPublicId(templatePublicId);
        Set<String> taskTypeKeys = new java.util.LinkedHashSet<>();
        List<ExamDefinitionSlotFeasibilityResponse> slots = new ArrayList<>();
        for (ExamDefinitionItemResponse item : template.items()) {
            try {
                String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                        item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
                taskTypeKeys.add(taskTypeKey);
            } catch (RuntimeException ex) {
                slots.add(new ExamDefinitionSlotFeasibilityResponse(item.taskTypeKey(), item.section(),
                        item.maxCount(), 0, false, ExamDefinitionConstants.UNKNOWN_TASK_TYPE));
            }
        }
        Map<String, Long> availability = itembankService.countPublishedByTaskTypeKeys(taskTypeKeys);
        Set<TaskRuntimeProfileDescriptor> invalidProfiles = runtimeProfileService == null ? Set.of()
                : runtimeProfileService.invalidPinnedProfiles(template.items().stream()
                        .filter(item -> item.taskTypeKey() == null
                                || TaskTypeCodeCompatibility.isStandard(item.taskTypeKey()))
                        .map(ExamDefinitionItemResponse::runtime)
                        .filter(java.util.Objects::nonNull)
                        .toList());
        for (ExamDefinitionItemResponse item : template.items()) {
            try {
                String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                        item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
                long available = availability.getOrDefault(taskTypeKey, 0L);
                boolean sectionMatches = item.section() != null && !item.section().isBlank();
                String profileReason = runtimeProfileReason(item, invalidProfiles);
                boolean ready = profileReason == null && sectionMatches && available >= item.maxCount();
                String reason = profileReason != null ? profileReason
                        : !sectionMatches ? "SECTION_MISMATCH" : ready ? null : "INSUFFICIENT_POOL";
                slots.add(new ExamDefinitionSlotFeasibilityResponse(taskTypeKey, item.section(),
                        item.maxCount(), available, ready, reason));
            } catch (RuntimeException ignored) {
                // The invalid slot was already added above with a stable reason.
            }
        }
        return new ExamDefinitionFeasibilityResponse(template.publicId(), template.version(),
                slots.stream().allMatch(ExamDefinitionSlotFeasibilityResponse::ready), slots);
    }

    private String runtimeProfileReason(ExamDefinitionItemResponse item,
            Set<TaskRuntimeProfileDescriptor> invalidProfiles) {
        if (item.runtime() == null) {
            return ExamDefinitionConstants.RUNTIME_PROFILE_NOT_PINNED;
        }
        if (item.taskTypeKey() != null && !TaskTypeCodeCompatibility.isStandard(item.taskTypeKey())
                && (item.runtime().screenKey() == null || item.runtime().contractVersion() < 1
                || item.runtime().answerSchemaVersion() < 1 || item.runtime().scoringMode() == null)) {
            return ExamDefinitionConstants.RUNTIME_PROFILE_INVALID;
        }
        if (invalidProfiles.contains(item.runtime())) {
            return ExamDefinitionConstants.RUNTIME_PROFILE_INVALID;
        }
        return null;
    }
}
