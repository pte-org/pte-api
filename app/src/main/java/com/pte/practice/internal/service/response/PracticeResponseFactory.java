package com.pte.practice.internal.service.response;

import com.pte.itembank.PracticeQuestionService;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.itembank.dto.response.PracticeQuestionPin;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeMockTestItem;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.mocktest.PracticeMockTestService;
import com.pte.practice.internal.service.selection.PracticeQuestionPicker;
import com.pte.practice.internal.service.selection.PracticeQuestionAllocation;
import com.pte.practice.internal.service.timing.PracticeTimingService;

/** Picks and pins immutable question content and timing when a practice session begins. */
public class PracticeResponseFactory {
    private static final Logger log = LoggerFactory.getLogger(PracticeResponseFactory.class);
    private final PracticeResponseRepository itemRepository;
    private final PracticeCatalogService catalogService;
    private final PracticeQuestionService practiceQuestionService;
    private final JsonMapper jsonMapper;
    private final PracticeQuestionPicker questionPicker;
    private final PracticeTimingService timingService;
    private PracticeMockTestService mockTestService;

    public PracticeResponseFactory(PracticeResponseRepository itemRepository, PracticeCatalogService catalogService,
            PracticeQuestionService practiceQuestionService, JsonMapper jsonMapper,
            PracticeQuestionPicker questionPicker, PracticeTimingService timingService) {
        this.itemRepository = itemRepository;
        this.catalogService = catalogService;
        this.practiceQuestionService = practiceQuestionService;
        this.jsonMapper = jsonMapper;
        this.questionPicker = questionPicker;
        this.timingService = timingService;
    }

    public void setMockTestService(PracticeMockTestService service) {
        this.mockTestService = service;
    }

    /**
     * Creates the session's items once, at begin: up to {@code questionCount} distinct questions per selected task
     * type (one for sessions started before V3), unseen questions first, with content frozen in one batch. A
     * TIMED session pins the active template's timing on every item; if the template no longer covers the chosen
     * task types, the session falls back to UNTIMED rather than getting stuck before its first item.
     */
    public void createItemsIfNeeded(PracticeSession session) {
        if (session.getId() == null || !itemRepository.findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId()).isEmpty()) {
            return;
        }
        if (session.effectiveKind() == PracticeSessionKind.MOCK_TEST) {
            createMockTestItems(session);
            return;
        }

        List<PracticeCatalogTaskResponse> tasks = catalogService.resolveStartableTasks(
                session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));
        Map<String, PracticeTimingService.Timing> timings = timingsFor(session, tasks);
        List<PracticeResponse> items = practiceQuestionService == null
                ? tasks.stream().map(task -> newItem(session, task)).toList()
                : pickAndPin(session, tasks, timings);
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setOrderIndex(index);
        }
        if (!items.isEmpty()) {
            itemRepository.saveAll(items);
        }
    }

    /**
     * Items of a mock test come from its pinned question list, in the stored order. Unlike a normal session this is
     * tolerant: a question that was unpublished since the test was built, or a task type the client cannot run, is
     * left out with a warning instead of blocking the whole test, and each item gets its own timing (or none).
     */
    private void createMockTestItems(PracticeSession session) {
        List<PracticeMockTestItem> pinned = mockTestService.itemsOf(session.getMockTestId());
        Map<String, PracticeCatalogTaskResponse> tasks = new HashMap<>();
        catalogService.getCatalog().sections().forEach(section -> section.taskTypes()
                .forEach(task -> tasks.put(PracticeTimingService.normalize(task.code()), task)));
        Map<UUID, PracticeQuestionPin> pins = new HashMap<>();
        practiceQuestionService.findPublishedQuestionsForPinning(
                pinned.stream().map(PracticeMockTestItem::getQuestionPublicId).toList())
                .forEach(pin -> pins.put(pin.content().publicId(), pin));
        Map<String, PracticeTimingService.Timing> timings = session.effectiveTimingMode() == PracticeTimingMode.TIMED
                && timingService != null ? timingService.activeTimings() : Map.of();
        if (session.effectiveTimingMode() == PracticeTimingMode.TIMED && timings.isEmpty()) {
            session.setTimingMode(PracticeTimingMode.UNTIMED);
        }

        List<PracticeResponse> items = new ArrayList<>();
        for (PracticeMockTestItem entry : pinned) {
            PracticeCatalogTaskResponse task = tasks.get(PracticeTimingService.normalize(entry.getTaskCode()));
            PracticeQuestionPin pin = pins.get(entry.getQuestionPublicId());
            if (task == null || task.availability() != PracticeCatalogAvailability.RUNNABLE || pin == null) {
                log.warn("Mock test {} item {} ({}) is skipped: task runnable={}, question published={}",
                        session.getMockTestId(), entry.getOrderIndex(), entry.getTaskCode(),
                        task != null && task.availability() == PracticeCatalogAvailability.RUNNABLE, pin != null);
                continue;
            }
            PracticeResponse item = newItem(session, task);
            pinQuestionContent(item, pin);
            PracticeTimingService.Timing timing = timings.get(PracticeTimingService.normalize(entry.getTaskCode()));
            if (timing != null) {
                item.setPreListenSeconds(timing.preListenSeconds());
                item.setPreRecordSeconds(timing.preRecordSeconds());
                item.setResponseSeconds(timing.responseSeconds());
            }
            item.setOrderIndex(items.size());
            items.add(item);
        }
        if (items.isEmpty()) {
            throw contentNotReady();
        }
        itemRepository.saveAll(items);
    }

    private Map<String, PracticeTimingService.Timing> timingsFor(PracticeSession session,
            List<PracticeCatalogTaskResponse> tasks) {
        if (session.effectiveTimingMode() != PracticeTimingMode.TIMED) {
            return Map.of();
        }
        Map<String, PracticeTimingService.Timing> timings = timingService == null ? Map.of()
                : timingService.activeTimings();
        List<String> codes = tasks.stream().map(PracticeCatalogTaskResponse::code).toList();
        if (timingService == null || !timingService.covers(timings, codes)) {
            log.warn("Timed practice session {} has no template timing for {}; continuing untimed",
                    session.getPublicId(), codes);
            session.setTimingMode(PracticeTimingMode.UNTIMED);
            return Map.of();
        }
        return timings;
    }

    private List<PracticeResponse> pickAndPin(PracticeSession session, List<PracticeCatalogTaskResponse> tasks,
            Map<String, PracticeTimingService.Timing> timings) {
        int[] allocation = allocationFor(session, tasks.size());
        Set<UUID> seen = new HashSet<>(itemRepository.findSeenSourceQuestionIds(session.getStudentPublicId(),
                tasks.stream().map(PracticeCatalogTaskResponse::code).toList()));
        Map<PracticeCatalogTaskResponse, List<UUID>> pickedByTask = new LinkedHashMap<>();
        List<UUID> allPicked = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            PracticeCatalogTaskResponse task = tasks.get(index);
            if (allocation[index] <= 0) {
                continue; // a skill session with fewer questions than task types leaves the last types out
            }
            List<UUID> picked = questionPicker.pick(
                    practiceQuestionService.findPublishedCandidateIds(task.code()), seen, allocation[index]);
            if (picked.isEmpty() && session.effectiveKind() == PracticeSessionKind.TASK) {
                throw contentNotReady();
            }
            if (!picked.isEmpty()) {
                pickedByTask.put(task, picked);
                allPicked.addAll(picked);
            }
        }
        Map<UUID, PracticeQuestionPin> pins = new HashMap<>();
        practiceQuestionService.findPublishedQuestionsForPinning(allPicked)
                .forEach(pin -> pins.put(pin.content().publicId(), pin));

        List<PracticeResponse> items = new ArrayList<>();
        pickedByTask.forEach((task, picked) -> {
            PracticeTimingService.Timing timing = timings.get(PracticeTimingService.normalize(task.code()));
            for (UUID questionId : picked) {
                PracticeQuestionPin pin = pins.get(questionId);
                if (pin == null) {
                    // Unpublished between picking and pinning; the other picks still make a usable session.
                    continue;
                }
                PracticeResponse item = newItem(session, task);
                pinQuestionContent(item, pin);
                if (timing != null) {
                    item.setPreListenSeconds(timing.preListenSeconds());
                    item.setPreRecordSeconds(timing.preRecordSeconds());
                    item.setResponseSeconds(timing.responseSeconds());
                }
                items.add(item);
            }
        });
        if (items.isEmpty()) {
            throw contentNotReady();
        }
        return items;
    }

    /** Questions to pick per task type: the chosen count each, or a skill session's total split across its types. */
    private int[] allocationFor(PracticeSession session, int taskCount) {
        int count = session.getQuestionCount() == null
                ? PracticeConstants.PRACTICE_LEGACY_QUESTION_COUNT : session.getQuestionCount();
        if (session.effectiveKind() == PracticeSessionKind.SKILL) {
            return PracticeQuestionAllocation.distribute(count, taskCount);
        }
        int[] each = new int[taskCount];
        java.util.Arrays.fill(each, count);
        return each;
    }

    private PracticeResponse newItem(PracticeSession session, PracticeCatalogTaskResponse task) {
        PracticeResponse item = new PracticeResponse();
        item.setPracticeSessionId(session.getId());
        item.setTaskCode(task.code());
        item.setDisplayName(task.displayName());
        item.setSection(task.section());
        item.setRendererKey(task.rendererKey());
        item.setContractVersion(task.contractVersion());
        item.setAnswerSchemaVersion(task.answerSchemaVersion());
        item.setStatus(PracticeResponseStatus.PENDING);
        return item;
    }

    private PracticeSessionException contentNotReady() {
        return new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
    }

    private Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void pinQuestionContent(PracticeResponse item, PracticeQuestionPin pin) {
        PracticeQuestionContent content = pin.content();
        item.setSourceQuestionPublicId(content.publicId());
        item.setPromptText(content.promptText());
        item.setMinWordCount(content.minWordCount());
        item.setMaxWordCount(content.maxWordCount());
        item.setAudioPromptRef(content.audioPromptRef());
        item.setImagePromptRef(content.imagePromptRef());
        PracticeQuestionEvaluationContent evaluationContent = pin.evaluation();
        item.setReferenceAnswerText(evaluationContent.referenceAnswerText());
        item.setCorrectAnswerText(evaluationContent.correctAnswerText());
        try {
            item.setOptionsJson(jsonMapper.writeValueAsString(content.options()));
            item.setObjectiveOptionsJson(jsonMapper.writeValueAsString(evaluationContent.options()));
        } catch (RuntimeException ex) {
            throw new PracticeSessionException(HttpStatus.SERVICE_UNAVAILABLE,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
        }
    }
}
