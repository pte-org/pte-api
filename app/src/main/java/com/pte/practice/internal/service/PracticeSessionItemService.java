package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the item boundary inside a practice session. The session service keeps
 * the workflow and idempotency rules; this service keeps item persistence and
 * state transitions together.
 */
@Service
public class PracticeSessionItemService {

    private final PracticeSessionItemRepository itemRepository;
    private final PracticeCatalogService catalogService;
    private final PracticeAnswerValidationService answerValidationService;

    public PracticeSessionItemService(PracticeSessionItemRepository itemRepository,
            PracticeCatalogService catalogService, PracticeAnswerValidationService answerValidationService) {
        this.itemRepository = itemRepository;
        this.catalogService = catalogService;
        this.answerValidationService = answerValidationService;
    }

    public void createItemsIfNeeded(PracticeSession session) {
        if (session.getId() == null || !itemsFor(session).isEmpty()) {
            return;
        }

        List<PracticeCatalogTaskResponse> tasks = catalogService.resolveStartableTasks(
                session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));
        List<PracticeSessionItem> items = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            PracticeCatalogTaskResponse task = tasks.get(index);
            PracticeSessionItem item = new PracticeSessionItem();
            item.setPracticeSessionId(session.getId());
            item.setOrderIndex(index);
            item.setTaskCode(task.code());
            item.setDisplayName(task.displayName());
            item.setSection(task.section());
            item.setRendererKey(task.rendererKey());
            item.setContractVersion(task.contractVersion());
            item.setAnswerSchemaVersion(task.answerSchemaVersion());
            item.setStatus(PracticeSessionItemStatus.PENDING);
            items.add(item);
        }
        if (!items.isEmpty()) {
            itemRepository.saveAll(items);
        }
    }

    public List<PracticeSessionItem> itemsFor(PracticeSession session) {
        if (session.getId() == null) {
            return List.of();
        }
        List<PracticeSessionItem> items = itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId());
        return items == null ? List.of() : items;
    }

    public PracticeSessionItem findForUpdate(PracticeSession session, UUID itemPublicId) {
        return itemRepository.findWithLockByPublicIdAndPracticeSessionId(itemPublicId, session.getId())
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    public void requirePending(PracticeSessionItem item) {
        if (item.getStatus() != PracticeSessionItemStatus.PENDING) {
            throw notStartable();
        }
    }

    public void answer(PracticeSessionItem item, PracticeAnswerRequest request, Instant now) {
        requirePending(item);
        answerValidationService.validate(item.getRendererKey(), item.getAnswerSchemaVersion(),
                request.payload(), request.confidence());
        item.setSavedPayload(request.payload());
        item.setConfidence(request.confidence());
        item.setStatus(PracticeSessionItemStatus.ANSWERED);
        item.setAnsweredAt(now);
        itemRepository.saveAndFlush(item);
    }

    public void saveDraft(PracticeSessionItem item, String payload, ResponseConfidence confidence) {
        requirePending(item);
        answerValidationService.validateDraft(item.getRendererKey(), item.getAnswerSchemaVersion(), payload, confidence);
        item.setSavedPayload(payload);
        item.setConfidence(confidence);
        itemRepository.saveAndFlush(item);
    }

    public void skip(PracticeSessionItem item, Instant now) {
        requirePending(item);
        item.setStatus(PracticeSessionItemStatus.SKIPPED);
        item.setSkippedAt(now);
        itemRepository.saveAndFlush(item);
    }

    public void completeIfFinished(PracticeSession session, Instant now) {
        List<PracticeSessionItem> items = itemsFor(session);
        if (!items.isEmpty() && items.stream().noneMatch(item -> item.getStatus() == PracticeSessionItemStatus.PENDING)) {
            session.setStatus(PracticeSessionStatus.COMPLETED);
            session.setCompletedAt(now);
        }
    }

    public boolean hasProgress(PracticeSession session) {
        return itemsFor(session).stream().anyMatch(item -> item.getStatus() == PracticeSessionItemStatus.ANSWERED
                || (item.getSavedPayload() != null && !item.getSavedPayload().isBlank()));
    }

    private PracticeSessionException notStartable() {
        return new PracticeSessionException(HttpStatus.CONFLICT,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE);
    }

    private Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
