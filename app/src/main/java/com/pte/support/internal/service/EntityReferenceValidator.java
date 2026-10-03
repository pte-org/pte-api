package com.pte.support.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.itembank.ItembankService;
import com.pte.session.SessionService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.internal.exception.EntityReferenceNotFoundException;
import com.pte.support.internal.exception.InvalidTicketEntityPairException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EntityReferenceValidator {

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
