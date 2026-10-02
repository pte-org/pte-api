package com.pte.scoretemplate;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.itembank.ItembankService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateSlotFeasibilityResponse;
import com.pte.scoretemplate.internal.exception.NoActiveScoreTemplateException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotFoundException;
import com.pte.scoretemplate.internal.mapper.ScoreTemplateMapper;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
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
 * The only door other modules use to reach {@code scoretemplate}. {@code
 * ScoreTemplateRepository}, the admin service, and the controller stay in
 * {@code internal/}. Read-only and unauthenticated by design — callers
 * ({@code assessment} pinning at publish, {@code attempt} for timing,
 * {@code scoring} for scoringMethod, {@code reporting} for weights) are
 * trusted in-process application code, not end users; PLATFORM_ADMIN write
 * access is enforced only at {@code ScoreTemplateController}.
 */
@Service
public class ScoreTemplateService {

    private final ScoreTemplateRepository repository;
    private final ItembankService itembankService;
    private final TaskRuntimeProfileService runtimeProfileService;

    @Autowired
    public ScoreTemplateService(ScoreTemplateRepository repository, ItembankService itembankService,
            TaskRuntimeProfileService runtimeProfileService) {
        this.repository = repository;
        this.itembankService = itembankService;
        this.runtimeProfileService = runtimeProfileService;
    }

    /** Compatibility constructor for focused readiness tests. */
    public ScoreTemplateService(ScoreTemplateRepository repository, ItembankService itembankService) {
        this(repository, itembankService, null);
    }

    /** Compatibility constructor for focused read-service tests. */
    public ScoreTemplateService(ScoreTemplateRepository repository) {
        this(repository, null, null);
    }

    /** Used by {@code assessment.SnapshotPublishService} to pin the scoring scheme at exam-publish time. */
    @Transactional(readOnly = true)
    public ScoreTemplateResponse getActive() {
        ScoreTemplate active = repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)
                .orElseThrow(NoActiveScoreTemplateException::new);
        return ScoreTemplateMapper.toResponse(active);
    }

    /**
     * Used by everything that already has a pinned {@code scoreTemplatePublicId}
     * (attempt/scoring/reporting) — deliberately does NOT filter by status, so
     * a snapshot pinned to a now-RETIRED template still resolves (immutability
     * guarantee: a published exam's scoring never drifts).
     */
    @Transactional(readOnly = true)
    public ScoreTemplateResponse getByPublicId(UUID publicId) {
        ScoreTemplate template = repository.findWithItemsByPublicId(publicId)
                .orElseThrow(ScoreTemplateNotFoundException::new);
        return ScoreTemplateMapper.toResponse(template);
    }

    /** Resolves pinned templates for a report cohort with one item-fetch query. */
    @Transactional(readOnly = true)
    public Map<UUID, ScoreTemplateResponse> getByPublicIds(Collection<UUID> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(publicIds);
        Map<UUID, ScoreTemplateResponse> templates = repository.findAllWithItemsByPublicIds(List.copyOf(uniqueIds))
                .stream()
                .map(ScoreTemplateMapper::toResponse)
                .collect(java.util.stream.Collectors.toMap(ScoreTemplateResponse::publicId, template -> template));
        if (templates.size() != uniqueIds.size()) {
            throw new ScoreTemplateNotFoundException();
        }
        return Map.copyOf(templates);
    }

    /** Returns a template only when it is currently ACTIVE, without exposing its internal enum. */
    @Transactional(readOnly = true)
    public Optional<ScoreTemplateResponse> findActiveByPublicId(UUID publicId) {
        return repository.findWithItemsByPublicId(publicId)
                .filter(template -> template.getStatus() == ScoreTemplateStatus.ACTIVE)
                .map(ScoreTemplateMapper::toResponse);
    }

    /** Answer-free catalog readiness report used by authoring and exam orchestration. */
    @Transactional(readOnly = true)
    public ScoreTemplateFeasibilityResponse getTemplateFeasibility(UUID templatePublicId) {
        if (itembankService == null) {
            throw new IllegalStateException(ScoreTemplateConstants.TEMPLATE_FEASIBILITY_DEPENDENCIES_NOT_CONFIGURED);
        }
        ScoreTemplateResponse template = getByPublicId(templatePublicId);
        Set<String> taskTypeKeys = new java.util.LinkedHashSet<>();
        List<ScoreTemplateSlotFeasibilityResponse> slots = new ArrayList<>();
        for (ScoreTemplateItemResponse item : template.items()) {
            try {
                String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                        item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
                taskTypeKeys.add(taskTypeKey);
            } catch (RuntimeException ex) {
                slots.add(new ScoreTemplateSlotFeasibilityResponse(item.taskTypeKey(), item.section(),
                        item.maxCount(), 0, false, ScoreTemplateConstants.UNKNOWN_TASK_TYPE));
            }
        }
        Map<String, Long> availability = itembankService.countPublishedByTaskTypeKeys(taskTypeKeys);
        Set<TaskRuntimeProfileDescriptor> invalidProfiles = runtimeProfileService == null ? Set.of()
                : runtimeProfileService.invalidPinnedProfiles(template.items().stream()
                        .filter(item -> item.taskTypeKey() == null
                                || TaskTypeCodeCompatibility.isStandard(item.taskTypeKey()))
                        .map(ScoreTemplateItemResponse::runtime)
                        .filter(java.util.Objects::nonNull)
                        .toList());
        for (ScoreTemplateItemResponse item : template.items()) {
            try {
                String taskTypeKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                        item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
                long available = availability.getOrDefault(taskTypeKey, 0L);
                boolean sectionMatches = item.section() != null && !item.section().isBlank();
                String profileReason = runtimeProfileReason(item, invalidProfiles);
                boolean ready = profileReason == null && sectionMatches && available >= item.maxCount();
                String reason = profileReason != null ? profileReason
                        : !sectionMatches ? "SECTION_MISMATCH" : ready ? null : "INSUFFICIENT_POOL";
                slots.add(new ScoreTemplateSlotFeasibilityResponse(taskTypeKey, item.section(),
                        item.maxCount(), available, ready, reason));
            } catch (RuntimeException ignored) {
                // The invalid slot was already added above with a stable reason.
            }
        }
        return new ScoreTemplateFeasibilityResponse(template.publicId(), template.version(),
                slots.stream().allMatch(ScoreTemplateSlotFeasibilityResponse::ready), slots);
    }

    private String runtimeProfileReason(ScoreTemplateItemResponse item,
            Set<TaskRuntimeProfileDescriptor> invalidProfiles) {
        if (item.runtime() == null) {
            return ScoreTemplateConstants.RUNTIME_PROFILE_NOT_PINNED;
        }
        if (item.taskTypeKey() != null && !TaskTypeCodeCompatibility.isStandard(item.taskTypeKey())
                && (item.runtime().screenKey() == null || item.runtime().contractVersion() < 1
                || item.runtime().answerSchemaVersion() < 1 || item.runtime().scoringMode() == null)) {
            return ScoreTemplateConstants.RUNTIME_PROFILE_INVALID;
        }
        if (invalidProfiles.contains(item.runtime())) {
            return ScoreTemplateConstants.RUNTIME_PROFILE_INVALID;
        }
        return null;
    }
}
