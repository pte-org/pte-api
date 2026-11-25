package com.pte.support.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.itembank.ItembankService;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.session.SessionService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.internal.exception.EntityReferenceNotFoundException;
import com.pte.support.internal.dto.response.TicketEntityContext;
import com.pte.support.internal.exception.InvalidTicketEntityPairException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EntityReferenceValidator {

    private static final int PROMPT_SNIPPET_MAX = 200;

    private final ItembankService itembankService;
    private final SessionService sessionService;
    private final AttemptService attemptService;

    public EntityReferenceValidator(ItembankService itembankService, SessionService sessionService,
            AttemptService attemptService) {
        this.itembankService = itembankService;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
    }

    public void validate(TicketEntityType entityType, String entityId, CurrentUser caller) {
        boolean typePresent = entityType != null;
        boolean idPresent = entityId != null && !entityId.isBlank();
        if (!typePresent && !idPresent) {
            return;
        }
        if (typePresent != idPresent) {
            throw new InvalidTicketEntityPairException();
        }
        UUID id;
        try {
            id = UUID.fromString(entityId);
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketEntityPairException();
        }
        switch (entityType) {
            case QUESTION -> validateQuestion(id, caller);
            case EXAM_SESSION -> validateSession(id, caller);
            case EXAM_ATTEMPT -> validateAttempt(id, caller);
        }
    }

    /**
     * Describes the entity a ticket links to, for display. Null when nothing is linked, the id is malformed,
     * the entity no longer exists or its type is not described yet; a missing entity never fails a ticket read.
     */
    public TicketEntityContext describe(TicketEntityType entityType, String entityId, CurrentUser caller) {
        if (entityType != TicketEntityType.QUESTION || entityId == null || entityId.isBlank()) {
            return null;
        }
        try {
            QuestionResponse question = itembankService.get(UUID.fromString(entityId), caller);
            return new TicketEntityContext(question.taskTypeKey(), question.section(),
                    snippet(question.promptText()));
        } catch (IllegalArgumentException | DomainException e) {
            return null;
        }
    }

    private static String snippet(String promptText) {
        if (promptText == null) {
            return null;
        }
        String collapsed = promptText.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() <= PROMPT_SNIPPET_MAX
                ? collapsed : collapsed.substring(0, PROMPT_SNIPPET_MAX).stripTrailing() + "…";
    }

    private void validateQuestion(UUID questionId, CurrentUser caller) {
        try {
            itembankService.get(questionId, caller);
        } catch (DomainException e) {
            throw new EntityReferenceNotFoundException();
        }
    }

    private void validateSession(UUID sessionId, CurrentUser caller) {
        try {
            sessionService.verifyHostAccess(sessionId, caller.tenantId());
        } catch (DomainException e) {
            throw new EntityReferenceNotFoundException();
        }
    }

    private void validateAttempt(UUID attemptId, CurrentUser caller) {
        if (!attemptService.attemptExistsForTenant(attemptId, caller.tenantId())) {
            throw new EntityReferenceNotFoundException();
        }
    }
}
