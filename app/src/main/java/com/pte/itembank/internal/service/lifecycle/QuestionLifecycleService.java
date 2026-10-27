package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.internal.service.QuestionMediaReferenceValidator;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.dto.request.RejectQuestionRequest;
import com.pte.itembank.dto.request.UpdateQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.exception.QuestionVersionConflictException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.constant.SharedConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles question lifecycle mutations: create, update, archive, unarchive, revisions.
 * Approval workflows delegated to QuestionApprovalService. Extracted from ItembankService.
 */
@Service
public class QuestionLifecycleService {

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final ItembankAccessPolicy accessPolicy;
    private final AuditLogService auditLogService;
    private final QuestionApprovalService approvalService;
    private final QuestionMediaReferenceValidator mediaValidator;

    @Autowired
    public QuestionLifecycleService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                                   ItembankAccessPolicy accessPolicy, AuditLogService auditLogService,
                                   QuestionApprovalService approvalService,
                                   QuestionMediaReferenceValidator mediaValidator) {
        this.mediaValidator = mediaValidator;
        this.questionRepository = questionRepository;
        this.validationHelper = validationHelper;
        this.accessPolicy = accessPolicy;
        this.auditLogService = auditLogService;
        this.approvalService = approvalService;
    }

    public QuestionLifecycleService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                                   ItembankAccessPolicy accessPolicy, AuditLogService auditLogService) {
        this(questionRepository, validationHelper, accessPolicy, auditLogService,
                new QuestionApprovalService(questionRepository, validationHelper, accessPolicy, auditLogService,
                        new QuestionMediaReferenceValidator(null)),
                new QuestionMediaReferenceValidator(null));
    }

    @Transactional
    public QuestionResponse createRevision(UUID publicId, CurrentUser caller) {
        Question source = loadForPlatformWrite(publicId, caller);
        if (source.getStatus() != QuestionStatus.APPROVED || !source.isCurrent()) {
            throw new InvalidQuestionStatusTransitionException();
        }
        Question revision = new Question();
        revision.setPteTaskType(source.getPteTaskType());
        revision.setTaskTypeKey(source.getTaskTypeKey() == null && source.getPteTaskType() != null
                ? source.getPteTaskType().name() : source.getTaskTypeKey());
        revision.setTaskTypeSection(source.getTaskTypeSection() == null && source.getPteTaskType() != null
                ? source.getPteTaskType().getSection().name() : source.getTaskTypeSection());
        revision.setVisibility(source.getVisibility());
        revision.setPool(source.getPool());
        revision.setAvailableForPractice(source.isAvailableForPractice());
        revision.setAvailableForExam(source.isAvailableForExam());
        revision.setAvailabilityFlagsExplicit(source.isAvailabilityFlagsExplicit());
        revision.setTenantId(source.getTenantId());
        revision.setAuthorUserPublicId(caller.userId());
        revision.setStatus(QuestionStatus.DRAFT);
        revision.setEverPublished(false);
        revision.setRevisionGroupPublicId(source.getRevisionGroupPublicId());
        revision.setRevisionNumber(source.getRevisionNumber() + 1);
        revision.setSupersedesPublicId(source.getPublicId());
        revision.setCurrent(false);
        applyContent(revision, source.getTitle(), source.getPromptText(), source.getAudioPromptRef(),
                source.getImagePromptRef(), source.getReferenceAnswerText(), source.getCorrectAnswerText(),
                source.getMinWordCount(), source.getMaxWordCount(), source.getOptions().stream()
                        .map(o -> new OptionRequest(o.getText(), o.isCorrect(), o.getOrderIndex(),
                                o.getBlankIndex(), o.getCorrectGapIndex()))
                        .toList());
        Question saved = questionRepository.save(revision);
        audit(caller, ItembankConstants.AUDIT_REVISION_CREATED, saved,
                "Created question revision " + saved.getRevisionNumber());
        return toResponse(saved);
    }

    // Delegation to QuestionApprovalService
    @Transactional
    public QuestionResponse submitApproval(UUID publicId, CurrentUser caller) {
        return approvalService.submitApproval(publicId, caller);
    }

    @Transactional
    public QuestionResponse approve(UUID publicId, CurrentUser caller) {
        return approvalService.approve(publicId, caller);
    }

    @Transactional
    public QuestionResponse reject(UUID publicId, RejectQuestionRequest request, CurrentUser caller) {
        return approvalService.reject(publicId, request, caller);
    }

    @Transactional
    public QuestionResponse publish(UUID publicId, CurrentUser caller) {
        return approvalService.publish(publicId, caller);
    }

    @Transactional
    public QuestionResponse archive(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() == QuestionStatus.APPROVED || Boolean.TRUE.equals(question.getEverPublished())) {
            requireArchiveGovernance(caller, question.getAuthorUserPublicId());
        }
        if (question.getStatus() == QuestionStatus.ARCHIVED) return toResponse(question);
        if (!com.pte.itembank.internal.service.QuestionLifecyclePolicy.canArchive(question)) {
            throw new InvalidQuestionStatusTransitionException();
        }
        if (question.getStatus() == QuestionStatus.APPROVED) question.setEverPublished(true);
        question.setStatus(QuestionStatus.ARCHIVED);
        question.setCurrent(false);
        audit(caller, ItembankConstants.AUDIT_ARCHIVED, question, "Archived question");
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse unarchive(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (Boolean.TRUE.equals(question.getEverPublished())) {
            requireArchiveGovernance(caller, question.getAuthorUserPublicId());
        }
        if (question.getStatus() != QuestionStatus.ARCHIVED) {
            throw new InvalidQuestionStatusTransitionException();
        }
        if (question.getSupersedesPublicId() != null) {
            throw new InvalidQuestionStatusTransitionException();
        }
        question.setStatus(QuestionStatus.DRAFT);
        boolean groupHasCurrentElsewhere = questionRepository
                .existsByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(
                        question.getRevisionGroupPublicId(), question.getPublicId());
        question.setCurrent(!groupHasCurrentElsewhere);
        audit(caller, ItembankConstants.AUDIT_UNARCHIVED, question, "Restored question to draft");
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse update(UUID publicId, UpdateQuestionRequest request, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new InvalidQuestionStatusTransitionException();
        }
        if (request.version() != null && request.version() != question.getVersion()) {
            throw new QuestionVersionConflictException();
        }
        applyContent(question, request.title(), request.promptText(), request.audioPromptRef(),
                request.imagePromptRef(), request.referenceAnswerText(), request.correctAnswerText(),
                request.minWordCount(), request.maxWordCount(), request.options());
        if (request.availableForPractice() != null) {
            question.setAvailableForPractice(request.availableForPractice());
        }
        if (request.availableForExam() != null) {
            question.setAvailableForExam(request.availableForExam());
        }
        if (request.availableForPractice() != null || request.availableForExam() != null) {
            question.setAvailabilityFlagsExplicit(true);
        }
        validationHelper.validate(question);
        mediaValidator.validate(question, caller);
        audit(caller, ItembankConstants.AUDIT_UPDATED, question, "Updated question draft");
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

    private void requireArchiveGovernance(CurrentUser caller, UUID authorUserPublicId) {
        if (caller != null && caller.hasRole(SecurityRoles.ACADEMIC_STAFF) && authorUserPublicId == null) {
            return;
        }
        if (accessPolicy.canPublish(caller, authorUserPublicId)) {
            return;
        }
        if (caller != null && caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && authorUserPublicId != null && authorUserPublicId.equals(caller.userId())) {
            deny(caller, null, ItembankConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
        }
        deny(caller, null, ItembankConstants.ACADEMIC_PUBLISH_REQUIRED);
    }

    private void applyContent(Question question, String title, String promptText, UUID audioPromptRef,
            UUID imagePromptRef, String referenceAnswerText, String correctAnswerText, Integer minWordCount,
            Integer maxWordCount, List<OptionRequest> options) {
        question.setTitle(title);
        question.setPromptText(promptText);
        question.setAudioPromptRef(audioPromptRef);
        question.setImagePromptRef(imagePromptRef);
        question.setReferenceAnswerText(referenceAnswerText);
        question.setCorrectAnswerText(correctAnswerText);
        question.setMinWordCount(minWordCount);
        question.setMaxWordCount(maxWordCount);
        question.getOptions().clear();
        addOptions(question, options);
    }

    private void addOptions(Question question, List<OptionRequest> options) {
        if (options == null) return;
        options.forEach(source -> {
            QuestionOption option = new QuestionOption();
            option.setText(source.text());
            option.setCorrect(source.correct());
            option.setOrderIndex(source.orderIndex());
            option.setBlankIndex(source.blankIndex());
            option.setCorrectGapIndex(source.correctGapIndex());
            question.addOption(option);
        });
    }

    private void audit(CurrentUser caller, String action, Question question, String summary) {
        if (auditLogService == null || caller == null || question == null) return;
        String text = summary == null ? "" : summary;
        auditLogService.record(caller, ItembankConstants.QUESTION_AGGREGATE,
                String.valueOf(question.getPublicId()), action,
                text.length() <= 500 ? text : text.substring(0, 500));
    }

    private void deny(CurrentUser caller, UUID aggregateId, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ItembankConstants.QUESTION_AGGREGATE,
                    aggregateId == null ? "unknown" : aggregateId.toString(),
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
    }

    private QuestionResponse toResponse(Question question) {
        return QuestionMapper.toResponse(question);
    }
}
