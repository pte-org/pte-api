package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read-only review projection; it never evaluates or generates AI content. */
@Service
public class PracticeReviewService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionItemRepository itemRepository;

    public PracticeReviewService(PracticeSessionRepository sessionRepository,
            PracticeSessionItemRepository itemRepository) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public PracticeReviewResponse get(UUID sessionPublicId, CurrentUser caller) {
        PracticeSession session = sessionRepository
                .findByPublicIdAndStudentPublicIdAndDeletedFalse(sessionPublicId, caller.userId())
                .orElseThrow(com.pte.practice.internal.exception.PracticeSessionNotFoundException::new);
        return new PracticeReviewResponse(session.getPublicId(), itemRepository
                .findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(session.getId()).stream()
                .map(item -> new PracticeReviewResponse.Item(item.getPublicId(), item.getOrderIndex(), item.getTaskCode(),
                        item.getDisplayName(), item.getSection(), item.getStatus().name(), item.getPromptText(),
                        item.getSavedPayload(), item.getRendererKey()))
                .toList());
    }
}
