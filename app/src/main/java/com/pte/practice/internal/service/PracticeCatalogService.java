package com.pte.practice.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.request.PracticePreflightRequest;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.dto.response.PracticePreflightResponse;
import com.pte.practice.internal.exception.PracticeCatalogException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a safe student catalog from the canonical item-bank/runtime boundary.
 * This service never maps correct-answer or authoring-only fields into a
 * student response.
 */
@Service
public class PracticeCatalogService {

    private final QuestionTypeService questionTypeService;

    public PracticeCatalogService(QuestionTypeService questionTypeService) {
        this.questionTypeService = questionTypeService;
    }

    @Transactional(readOnly = true)
    public PracticeCatalogResponse getCatalog() {
        Map<String, PracticeCatalogTaskResponse> tasks = new LinkedHashMap<>();
        for (QuestionTypeResponse definition : questionTypeService.list(true)) {
            PracticeCatalogTaskResponse task = mapDefinition(definition);
            tasks.putIfAbsent(task.code(), task);
        }

        // Keep the shell discoverable if a persisted catalog row is missing.
        // Such entries are explicitly visible-only and cannot be selected for
        // execution until canonical content is published.
        for (PteTaskType taskType : PteTaskType.values()) {
            tasks.putIfAbsent(taskType.name(), fallbackTask(taskType));
        }
        tasks.putIfAbsent(PracticeConstants.PRACTICE_WRITE_EMAIL_TASK_CODE, new PracticeCatalogTaskResponse(
                PracticeConstants.PRACTICE_WRITE_EMAIL_TASK_CODE,
                PracticeConstants.PRACTICE_WRITE_EMAIL_TASK_LABEL, PteSection.WRITING.name(), true,
                PracticeCatalogAvailability.UNAVAILABLE, PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                null, null, null, null, null, List.of(), PracticeConstants.PRACTICE_BLOCKED_CONTRACT_STATUS,
                PracticeConstants.PRACTICE_REFERENCE_GAP_PROVENANCE));
        return new PracticeCatalogResponse(
                PracticeConstants.PRACTICE_PRODUCT_CODE,
                PracticeConstants.PRACTICE_PRODUCT_TITLE,
                PracticeConstants.PRACTICE_CATALOG_VERSION,
                PracticeConstants.PRACTICE_TIME_LIMIT_SECONDS,
                groupBySection(tasks.values()));
    }

    @Transactional(readOnly = true)
    public PracticePreflightResponse preflight(PracticePreflightRequest request) {
        requireProduct(request.productCode());
        PracticeCatalogResponse catalog = getCatalog();
        List<String> requested = normalizeCodes(request.safeTaskTypeCodes());
        Map<String, PracticeCatalogTaskResponse> byCode = index(catalog);
        Set<String> blocked = new LinkedHashSet<>();
        Set<String> missingCapabilities = new LinkedHashSet<>();
        Set<String> supported = request.capabilities() == null
                ? Set.of() : request.capabilities().safeCapabilities();

        for (String code : requested) {
            PracticeCatalogTaskResponse task = byCode.get(code);
            if (task == null || task.availability() != PracticeCatalogAvailability.RUNNABLE) {
                blocked.add(code);
                continue;
            }
            task.requiredClientCapabilities().stream()
                    .filter(capability -> !supported.contains(capability))
                    .forEach(missingCapabilities::add);
        }
        return new PracticePreflightResponse(
                catalog.productCode(), catalog.catalogVersion(),
                blocked.isEmpty() && missingCapabilities.isEmpty(),
                requested, List.copyOf(blocked), List.copyOf(missingCapabilities));
    }

    @Transactional(readOnly = true)
    public void requireStartable(String productCode, Collection<String> taskTypeCodes,
            PracticeCapabilityManifest capabilities) {
        requireProduct(productCode);
        PracticePreflightResponse preflight = preflight(new PracticePreflightRequest(
                productCode,
                taskTypeCodes == null ? Set.of() : Set.copyOf(taskTypeCodes),
                capabilities));
        if (!preflight.missingCapabilities().isEmpty()) {
            throw new PracticeCatalogException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_CAPABILITY_MISMATCH,
                    PracticeConstants.PRACTICE_CAPABILITY_MISMATCH_MESSAGE);
        }
        if (!preflight.blockedTaskTypes().isEmpty()) {
            Map<String, PracticeCatalogTaskResponse> byCode = index(getCatalog());
            boolean contentNotReady = preflight.blockedTaskTypes().stream()
                    .map(byCode::get)
                    .anyMatch(task -> task != null && task.availability() == PracticeCatalogAvailability.VISIBLE);
            throw new PracticeCatalogException(HttpStatus.UNPROCESSABLE_ENTITY,
                    contentNotReady ? PracticeConstants.PRACTICE_CONTENT_NOT_READY
                            : PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                    contentNotReady ? PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE
                            : PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE);
        }
    }

    private PracticeCatalogTaskResponse mapDefinition(QuestionTypeResponse definition) {
        String code = normalizeCode(definition.taskTypeCode() == null ? definition.code() : definition.taskTypeCode());
        String section = safeSection(definition.section(), code);
        TaskRuntimeProfileDescriptor profile = definition.runtime();
        if (profile == null) {
            profile = resolveProfile(code);
        }
        boolean allowlisted = profile != null && TaskRuntimeProfileRegistry.isAllowlistedContract(profile);
        boolean serverReady = definition.readiness() == null || definition.readiness().serverReady();
        boolean runnable = allowlisted && profile.active() && serverReady;
        PracticeCatalogAvailability availability = runnable
                ? PracticeCatalogAvailability.RUNNABLE
                : allowlisted ? PracticeCatalogAvailability.VISIBLE : PracticeCatalogAvailability.UNAVAILABLE;
        String reason = availability == PracticeCatalogAvailability.RUNNABLE ? null
                : availability == PracticeCatalogAvailability.VISIBLE
                        ? PracticeConstants.PRACTICE_CONTENT_NOT_READY
                        : PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME;
        return toTask(code, definition.displayName(), section, definition.scored(), profile,
                availability, reason,
                definition.readiness() == null ? PracticeConstants.PRACTICE_RUNTIME_CONTRACT_READY_STATUS :
                        (serverReady ? PracticeConstants.PRACTICE_SERVER_READY_STATUS
                                : PracticeConstants.PRACTICE_SERVER_NOT_READY_STATUS),
                PracticeConstants.PRACTICE_PERSISTED_CATALOG_PROVENANCE);
    }

    private PracticeCatalogTaskResponse fallbackTask(PteTaskType taskType) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.descriptorFor(taskType.name());
        return toTask(taskType.name(), humanize(taskType.name()), taskType.getSection().name(), taskType.isScored(),
                profile, PracticeCatalogAvailability.VISIBLE, PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                PracticeConstants.PRACTICE_RUNTIME_ONLY_STATUS,
                PracticeConstants.PRACTICE_RUNTIME_REGISTRY_PROVENANCE);
    }

    private PracticeCatalogTaskResponse toTask(String code, String displayName, String section, boolean scored,
            TaskRuntimeProfileDescriptor profile, PracticeCatalogAvailability availability, String reason,
            String contentStatus, String provenance) {
        return new PracticeCatalogTaskResponse(
                code,
                displayName == null || displayName.isBlank() ? humanize(code) : displayName,
                section,
                scored,
                availability,
                reason,
                profile == null ? null : profile.profileKey(),
                profile == null ? null : profile.profileVersion(),
                profile == null ? null : profile.rendererKey(),
                profile == null ? null : profile.contractVersion(),
                profile == null ? null : profile.answerSchemaVersion(),
                profile == null ? List.of() : profile.requiredClientCapabilities(),
                contentStatus,
                provenance);
    }

    private List<PracticeCatalogSectionResponse> groupBySection(Collection<PracticeCatalogTaskResponse> tasks) {
        Map<String, List<PracticeCatalogTaskResponse>> grouped = tasks.stream()
                .sorted(Comparator.comparingInt(this::sectionOrder).thenComparing(PracticeCatalogTaskResponse::code))
                .collect(Collectors.groupingBy(PracticeCatalogTaskResponse::section, LinkedHashMap::new,
                        Collectors.toList()));
        return grouped.entrySet().stream()
                .map(entry -> new PracticeCatalogSectionResponse(
                        entry.getKey(), humanize(entry.getKey()), entry.getValue()))
                .toList();
    }

    private int sectionOrder(PracticeCatalogTaskResponse task) {
        try {
            return PteSection.valueOf(task.section()).ordinal();
        } catch (RuntimeException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private Map<String, PracticeCatalogTaskResponse> index(PracticeCatalogResponse catalog) {
        return catalog.sections().stream()
                .flatMap(section -> section.taskTypes().stream())
                .collect(Collectors.toUnmodifiableMap(PracticeCatalogTaskResponse::code, task -> task,
                        (first, ignored) -> first));
    }

    private List<String> normalizeCodes(Collection<String> rawCodes) {
        if (rawCodes == null) {
            return List.of();
        }
        return rawCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return PracticeConstants.PRACTICE_UNKNOWN_TASK_CODE;
        }
        try {
            return TaskTypeCodeCompatibility.normalizeTaskTypeKey(code);
        } catch (RuntimeException ex) {
            return code.trim().toUpperCase(Locale.ROOT);
        }
    }

    private String safeSection(String rawSection, String taskCode) {
        if (rawSection != null && !rawSection.isBlank()) {
            return rawSection.trim().toUpperCase(Locale.ROOT);
        }
        try {
            return PteTaskType.valueOf(taskCode).getSection().name();
        } catch (RuntimeException ex) {
            return PracticeConstants.PRACTICE_UNKNOWN_TASK_CODE;
        }
    }

    private TaskRuntimeProfileDescriptor resolveProfile(String code) {
        try {
            return TaskRuntimeProfileRegistry.descriptorFor(code);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void requireProduct(String productCode) {
        if (!PracticeConstants.PRACTICE_PRODUCT_CODE.equals(productCode)) {
            throw new PracticeCatalogException(HttpStatus.NOT_FOUND,
                    PracticeConstants.PRACTICE_PRODUCT_NOT_FOUND,
                    PracticeConstants.PRACTICE_PRODUCT_NOT_FOUND_MESSAGE);
        }
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return PracticeConstants.PRACTICE_DEFAULT_DISPLAY_NAME;
        }
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        List<String> result = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", result);
    }
}
