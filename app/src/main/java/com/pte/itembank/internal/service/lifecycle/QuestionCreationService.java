package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.internal.service.QuestionMediaReferenceValidator;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.constant.SharedConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles question creation from request. Extracted from ItembankService to keep facade thin.
 */
@Service
public class QuestionCreationService {

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final ItembankAccessPolicy accessPolicy;
    private final QuestionTypeService questionTypeService;
    private final AuditLogService auditLogService;
    private final QuestionMediaReferenceValidator mediaValidator;

    public QuestionCreationService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                                   ItembankAccessPolicy accessPolicy, QuestionTypeService questionTypeService,
                                   AuditLogService auditLogService,
                                   QuestionMediaReferenceValidator mediaValidator) {
        this.mediaValidator = mediaValidator;
        this.questionRepository = questionRepository;
        this.validationHelper = validationHelper;
        this.accessPolicy = accessPolicy;
        this.questionTypeService = questionTypeService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public QuestionResponse create(CreateQuestionRequest request, CurrentUser caller) {
        if (!accessPolicy.canWrite(caller)) {
            deny(caller, null, ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }

        String taskTypeKey = resolveTaskTypeKey(request);
        PteTaskType taskType = parseStandardTaskTypeOrNull(taskTypeKey);
        if (questionTypeService != null && !questionTypeService.isActive(taskTypeKey)) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
        if (taskType == null && questionTypeService == null) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }

        Question question = new Question();
        question.setPteTaskType(taskType);
        question.setTaskTypeKey(taskTypeKey);
        question.setTaskTypeSection(resolveTaskTypeSection(taskTypeKey, taskType));
        question.setVisibility(Visibility.SHARED);
        QuestionPool pool = parsePool(request.pool());
        question.setPool(pool);
        boolean hasExplicitFlags = request.availableForPractice() != null || request.availableForExam() != null;
        question.setAvailableForPractice(hasExplicitFlags
                ? Boolean.TRUE.equals(request.availableForPractice()) : pool == QuestionPool.PRACTICE);
        question.setAvailableForExam(hasExplicitFlags
                ? Boolean.TRUE.equals(request.availableForExam()) : pool == QuestionPool.EXAM);
        question.setAvailabilityFlagsExplicit(true);
        question.setTenantId(null);
        question.setAuthorUserPublicId(caller.userId());
        question.setStatus(QuestionStatus.DRAFT);
        question.setEverPublished(false);
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setRevisionNumber(1);
        question.setCurrent(true);
        question.setTitle(request.title());
        question.setPromptText(request.promptText());
        question.setAudioPromptRef(request.audioPromptRef());
        question.setImagePromptRef(request.imagePromptRef());
        question.setReferenceAnswerText(request.referenceAnswerText());
        question.setCorrectAnswerText(request.correctAnswerText());
        question.setMinWordCount(request.minWordCount());
        question.setMaxWordCount(request.maxWordCount());
        addOptions(question, request.options());

        validationHelper.validate(question);
        mediaValidator.validate(question, caller);
        Question saved = questionRepository.save(question);
        audit(caller, ItembankConstants.AUDIT_CREATED, saved, ItembankConstants.AUDIT_SUMMARY_CREATED);
        return toResponse(saved);
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

    private String resolveTaskTypeKey(CreateQuestionRequest request) {
        String raw = request.taskTypeKey() == null || request.taskTypeKey().isBlank()
                ? request.pteTaskType() : request.taskTypeKey();
        if (raw == null || raw.isBlank()) {
            throw new QuestionValidationException(ItembankConstants.TASK_TYPE_REQUIRED);
        }
        try {
            return TaskTypeCodeCompatibility.normalizeTaskTypeKey(raw);
        } catch (RuntimeException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }

    private PteTaskType parseStandardTaskTypeOrNull(String taskTypeKey) {
        try {
            return PteTaskType.valueOf(taskTypeKey);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveTaskTypeSection(String taskTypeKey, PteTaskType taskType) {
        if (taskType != null) {
            return taskType.getSection().name();
        }
        if (questionTypeService != null) {
            return questionTypeService.findDefinitionByCode(taskTypeKey)
                    .map(definition -> definition.getSection().name())
                    .orElseThrow(() -> new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE));
        }
        throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
    }

    private QuestionPool parsePool(String value) {
        if (value == null || value.isBlank()) {
            return QuestionPool.EXAM;
        }
        try {
            return QuestionPool.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private void audit(CurrentUser caller, String action, Question question, String summary) {
        if (auditLogService == null || caller == null || question == null) return;
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
