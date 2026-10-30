package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.internal.dto.request.NavigateTaskRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.exception.answer.NotCurrentTaskException;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.policy.ManualNavigationPolicy;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.attempt.internal.service.capability.CapabilityNegotiationService;
import com.pte.shared.security.CurrentUser;

import java.util.List;

/**
 * Moves the live task pointer of an in-progress attempt and renders the task
 * responses (with their effective timers) the client receives.
 */
class AttemptTaskProgression {

    private final ExamAttemptRepository attemptRepository;
    private final ExamContentService contentService;
    private final TimerService timerService;
    private final AnswerSubmitService answerSubmitService;
    private final AttemptMapper attemptMapper;
    private final EncryptionKeyProvider encryptionKeyProvider;
    private final CapabilityNegotiationService capabilityNegotiationService;
    private final AttemptAccess access;

    AttemptTaskProgression(ExamAttemptRepository attemptRepository, ExamContentService contentService,
            TimerService timerService, AnswerSubmitService answerSubmitService, AttemptMapper attemptMapper,
            EncryptionKeyProvider encryptionKeyProvider, CapabilityNegotiationService capabilityNegotiationService,
            AttemptAccess access) {
        this.attemptRepository = attemptRepository;
        this.contentService = contentService;
        this.timerService = timerService;
        this.answerSubmitService = answerSubmitService;
        this.attemptMapper = attemptMapper;
        this.encryptionKeyProvider = encryptionKeyProvider;
        this.capabilityNegotiationService = capabilityNegotiationService;
        this.access = access;
    }

    /** Every task of an in-progress attempt, with timers computed at call time. */
    List<AttemptTaskResponse> allTasks(ExamAttempt attempt, CurrentUser caller) {
        assertStoredCapabilities(attempt, caller);
        List<ExamItemView> allItems = contentService.items(attempt);
        String encryptionPublicKey = "STRICT".equals(attempt.getAnswerIntegrityLevel())
                ? encryptionKeyProvider.getPublicKeyBase64()
                : null;
        return allItems.stream()
                .map(item -> {
                    int effectivePrep = timerService.resolveEffectivePrepSeconds(item);
                    int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, item, allItems);
                    return attemptMapper.toTaskResponse(attempt, item, effectivePrep, effectiveResponse,
                            allItems, encryptionPublicKey);
                })
                .toList();
    }

    /** Navigation body for an in-progress attempt whose session is already locked. */
    AttemptTaskResponse navigate(ExamAttempt attempt, NavigateTaskRequest request, CurrentUser caller) {
        attempt = access.lockAttempt(attempt);

        ExamItemView sourceItem = contentService.item(attempt, request.fromPinnedItemPublicId())
                .orElseThrow(NotCurrentTaskException::new);
        int sourceIndex = sourceItem.orderIndex();
        int targetIndex = sourceIndex + (request.direction() == NavigateTaskRequest.Direction.NEXT ? 1 : -1);
        long totalTasks = contentService.items(attempt).size();
        int currentIndex = attempt.getCurrentOrderIndex();

        // The answer-submit path may already have advanced one item before an
        // older client retries navigation. Reject anything further out of date.
        if (currentIndex != sourceIndex && currentIndex != sourceIndex + 1) {
            throw new NotCurrentTaskException();
        }

        if (targetIndex < 0) {
            return advanceUntilLiveOrComplete(attempt, caller);
        }

        if (targetIndex >= totalTasks) {
            if (!ManualNavigationPolicy.allowsSection(sourceItem.section())) {
                throw new NotCurrentTaskException();
            }
            answerSubmitService.submitIfAbsent(attempt, sourceItem, null);
            access.completeAttempt(attempt);
            return attemptMapper.toCompletedResponse(attempt);
        }

        ExamItemView targetItem = contentService.itemAt(attempt, targetIndex);
        if (!ManualNavigationPolicy.allowsSection(sourceItem.section())
                || !ManualNavigationPolicy.allowsSection(targetItem.section())) {
            throw new NotCurrentTaskException();
        }

        // A skipped task still needs a blank answer for scoring; an existing
        // answer from an earlier visit is left untouched.
        answerSubmitService.submitIfAbsent(attempt, sourceItem, null);
        if (currentIndex != targetIndex) {
            timerService.startTask(attempt, targetItem, contentService.items(attempt));
            attemptRepository.save(attempt);
        }
        return advanceUntilLiveOrComplete(attempt, caller);
    }

    /**
     * No expiry sweep — {@code currentOrderIndex} is already correct as
     * maintained by whichever call last advanced it ({@code createAndStart}/
     * {@link #advanceAfterCurrent}), so this is a pure read, needing no lock
     * (nothing here mutates {@code attempt}). Any drift now surfaces directly
     * as a data-integrity bug at its source, rather than being silently
     * patched over here.
     */
    AttemptTaskResponse advanceUntilLiveOrComplete(ExamAttempt attempt, CurrentUser caller) {
        assertStoredCapabilities(attempt, caller);
        List<ExamItemView> allItems = contentService.items(attempt);
        ExamItemView currentView = contentService.itemAt(attempt, attempt.getCurrentOrderIndex());
        int effectivePrep = timerService.resolveEffectivePrepSeconds(currentView);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, currentView, allItems);
        return attemptMapper.toTaskResponse(attempt, currentView, effectivePrep, effectiveResponse, allItems);
    }

    AttemptTaskResponse advanceAfterCurrent(ExamAttempt attempt, CurrentUser caller) {
        List<ExamItemView> allItems = contentService.items(attempt);
        int nextIndex = attempt.getCurrentOrderIndex() + 1;
        if (nextIndex >= allItems.size()) {
            access.completeAttempt(attempt);
            return attemptMapper.toCompletedResponse(attempt);
        }
        assertStoredCapabilities(attempt, caller);
        ExamItemView nextView = contentService.itemAt(attempt, nextIndex);
        timerService.startTask(attempt, nextView, allItems);
        int effectivePrep = timerService.resolveEffectivePrepSeconds(nextView);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, nextView, allItems);
        return attemptMapper.toTaskResponse(attempt, nextView, effectivePrep, effectiveResponse, allItems);
    }

    private void assertStoredCapabilities(ExamAttempt attempt, CurrentUser caller) {
        capabilityNegotiationService.assertStoredCapabilities(attempt, caller);
    }
}
