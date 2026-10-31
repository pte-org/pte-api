package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.internal.service.QuestionMediaReferenceValidator;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.dto.request.RejectQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.constant.SharedConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles question approval workflow: submitApproval, approve, reject, publish.
 * Extracted from QuestionLifecycleService to keep lifecycle service ≤300 lines.
 */
@Service
public class QuestionApprovalService {

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final ItembankAccessPolicy accessPolicy;
    private final AuditLogService auditLogService;
    private final QuestionMediaReferenceValidator mediaValidator;

    public QuestionApprovalService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                                   ItembankAccessPolicy accessPolicy, AuditLogService auditLogService,
                                   QuestionMediaReferenceValidator mediaValidator) {
        this.mediaValidator = mediaValidator;
        this.questionRepository = questionRepository;
        this.validationHelper = validationHelper;
        this.accessPolicy = accessPolicy;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public QuestionResponse submitApproval(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        mediaValidator.validate(question, caller);
        question.setRejectionReason(null);
        question.setStatus(QuestionStatus.PENDING_APPROVAL);
        audit(caller, ItembankConstants.AUDIT_SUBMITTED, question, ItembankConstants.AUDIT_SUMMARY_SUBMITTED);
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse approve(UUID publicId, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            deny(caller, publicId, ItembankConstants.ACADEMIC_REVIEW_REQUIRED);
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        requireAcademicReview(caller, question.getAuthorUserPublicId());
        if (question.getStatus() != QuestionStatus.PENDING_APPROVAL) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        mediaValidator.validate(question, caller);
        archiveSupersededQuestion(question);
        question.setRejectionReason(null);
        question.setCurrent(true);
        question.setStatus(QuestionStatus.APPROVED);
        question.setEverPublished(true);
        audit(caller, ItembankConstants.AUDIT_APPROVED, question, ItembankConstants.AUDIT_SUMMARY_APPROVED);
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse reject(UUID publicId, RejectQuestionRequest request, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            deny(caller, publicId, ItembankConstants.ACADEMIC_REVIEW_REQUIRED);
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        requireAcademicReview(caller, question.getAuthorUserPublicId());
        if (question.getStatus() != QuestionStatus.PENDING_APPROVAL) {
            throw new InvalidQuestionStatusTransitionException();
        }
        question.setStatus(QuestionStatus.DRAFT);
        question.setRejectionReason(request.reason());
        audit(caller, ItembankConstants.AUDIT_REJECTED, question, String.format(ItembankConstants.AUDIT_SUMMARY_REJECTED, request.reason()));
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse publish(UUID publicId, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            deny(caller, publicId, ItembankConstants.ACADEMIC_PUBLISH_REQUIRED);
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        requireAcademicPublish(caller, question.getAuthorUserPublicId());
        if (question.getStatus() == QuestionStatus.APPROVED) {
            return toResponse(question);
        }
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        mediaValidator.validate(question, caller);
        archiveSupersededQuestion(question);
        question.setRejectionReason(null);
        question.setCurrent(true);
        question.setStatus(QuestionStatus.APPROVED);
        question.setEverPublished(true);
        audit(caller, ItembankConstants.AUDIT_PUBLISHED, question, ItembankConstants.AUDIT_SUMMARY_PUBLISHED);
        return toResponse(question);
    }

    private Question loadForPlatformWrite(UUID publicId, CurrentUser caller) {
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (!accessPolicy.canModifyDraft(caller, question.getAuthorUserPublicId())) {
            deny(caller, publicId, ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
        return question;
    }

    private void requireAcademicReview(CurrentUser caller, UUID authorUserPublicId) {
        if (accessPolicy.canReview(caller, authorUserPublicId)) {
            return;
        }
        if (caller != null && caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && authorUserPublicId != null && authorUserPublicId.equals(caller.userId())) {
            deny(caller, null, ItembankConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
        }
        deny(caller, null, ItembankConstants.ACADEMIC_REVIEW_REQUIRED);
    }

    private void requireAcademicPublish(CurrentUser caller, UUID authorUserPublicId) {
        if (accessPolicy.canPublish(caller, authorUserPublicId)) {
            return;
        }
        if (caller != null && caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && authorUserPublicId != null && authorUserPublicId.equals(caller.userId())) {
            deny(caller, null, ItembankConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
        }
        deny(caller, null, ItembankConstants.ACADEMIC_PUBLISH_REQUIRED);
    }

    private void archiveSupersededQuestion(Question question) {
        if (question.getSupersedesPublicId() != null) {
            questionRepository.findWithOptionsByPublicId(question.getSupersedesPublicId()).ifPresent(previous -> {
                previous.setCurrent(false);
                previous.setStatus(QuestionStatus.ARCHIVED);
            });
        }
        questionRepository
                .findByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(question.getRevisionGroupPublicId(),
                        question.getPublicId())
                .forEach(other -> other.setCurrent(false));
        questionRepository.flush();
    }

    private void audit(CurrentUser caller, String action, Question question, String summary) {
        if (auditLogService == null || caller == null || question == null) {
            return;
        }
        String text = summary == null ? "" : summary;
        auditLogService.record(caller, ItembankConstants.QUESTION_AGGREGATE,
                String.valueOf(question.getPublicId()), action,
                text.length() <= ItembankConstants.AUDIT_SUMMARY_MAX_LENGTH ? text
                        : text.substring(0, ItembankConstants.AUDIT_SUMMARY_MAX_LENGTH));
    }

    private void deny(CurrentUser caller, UUID aggregateId, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ItembankConstants.QUESTION_AGGREGATE,
                    aggregateId == null ? ItembankConstants.AUDIT_UNKNOWN_AGGREGATE : aggregateId.toString(),
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
    }

    private QuestionResponse toResponse(Question question) {
        return QuestionMapper.toResponse(question);
    }
}
