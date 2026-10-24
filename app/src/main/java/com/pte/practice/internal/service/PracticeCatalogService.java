package com.pte.practice.internal.service;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.PracticeQuestionService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PracticeCatalogTaskMapper taskMapper;

    public PracticeCatalogService(QuestionTypeService questionTypeService) {
        this(questionTypeService, null);
    }

    @Autowired
    public PracticeCatalogService(QuestionTypeService questionTypeService,
            PracticeQuestionService practiceQuestionService) {
        this.questionTypeService = questionTypeService;
        this.taskMapper = new PracticeCatalogTaskMapper(practiceQuestionService);
    }

    @Transactional(readOnly = true)
    public PracticeCatalogResponse getCatalog() {
        Map<String, PracticeCatalogTaskResponse> tasks = new LinkedHashMap<>();
        for (QuestionTypeResponse definition : questionTypeService.list(true)) {
            PracticeCatalogTaskResponse task = taskMapper.mapDefinition(definition);
            if (!PracticeConstants.PRACTICE_HIDDEN_TASK_CODES.contains(task.code())) {
                tasks.putIfAbsent(task.code(), task);
            }
        }

        // Keep the shell discoverable if a persisted catalog row is missing.
        // Such entries are explicitly visible-only and cannot be selected for
        // execution until canonical content is published.
        for (PteTaskType taskType : PteTaskType.values()) {
            if (!PracticeConstants.PRACTICE_HIDDEN_TASK_CODES.contains(taskType.name())) {
                tasks.putIfAbsent(taskType.name(), taskMapper.fallbackTask(taskType));
            }
        }
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
                    .anyMatch(task -> task != null
                            && PracticeConstants.PRACTICE_CONTENT_NOT_READY.equals(task.unavailableReason()));
            throw new PracticeCatalogException(HttpStatus.UNPROCESSABLE_ENTITY,
                    contentNotReady ? PracticeConstants.PRACTICE_CONTENT_NOT_READY
                            : PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                    contentNotReady ? PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE
                            : PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE);
        }
    }

    /**
     * Resolves the immutable task contracts that will be pinned into a
     * session. The returned order is deterministic and never includes
     * unavailable or non-allowlisted tasks.
     */
    @Transactional(readOnly = true)
    public List<PracticeCatalogTaskResponse> resolveStartableTasks(String productCode,
            Collection<String> taskTypeCodes, PracticeCapabilityManifest capabilities) {
        requireStartable(productCode, taskTypeCodes, capabilities);
        Map<String, PracticeCatalogTaskResponse> byCode = index(getCatalog());
        List<String> requested = normalizeCodes(taskTypeCodes);
        return (requested.isEmpty() ? byCode.values().stream()
                .filter(task -> task.availability() == PracticeCatalogAvailability.RUNNABLE)
                .sorted(Comparator.comparing(PracticeCatalogTaskResponse::section)
                        .thenComparing(PracticeCatalogTaskResponse::code))
                .toList() : requested.stream().map(byCode::get).toList());
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
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("_");
        return java.util.Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
