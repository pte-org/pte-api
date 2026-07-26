package com.pte.authoring.service;

import com.pte.authoring.config.PteTaskTypeSkillMapping;
import com.pte.authoring.constant.AuthoringConstants;
import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.authoring.domain.enums.Skill;
import com.pte.authoring.domain.enums.Visibility;
import com.pte.authoring.domain.exception.QuestionNotFoundException;
import com.pte.authoring.domain.exception.QuestionValidationException;
import com.pte.authoring.domain.exception.SharedWriteForbiddenException;
import com.pte.authoring.dto.request.CreateQuestionRequest;
import com.pte.authoring.dto.request.OptionRequest;
import com.pte.authoring.dto.response.QuestionResponse;
import com.pte.authoring.mapper.QuestionMapper;
import com.pte.authoring.repository.QuestionRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Question authoring + lookup with SHARED/PRIVATE visibility enforcement
 * (ADR-001). A host writes only its own PRIVATE items; only a platform user
 * writes SHARED. Reads return SHARED plus the caller's own PRIVATE.
 */
@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final AuthoringAccessPolicy accessPolicy;
    private final PteTaskTypeSkillMapping skillMapping;

    public QuestionService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                           AuthoringAccessPolicy accessPolicy, PteTaskTypeSkillMapping skillMapping) {
        this.questionRepository = questionRepository;
        this.validationHelper = validationHelper;
        this.accessPolicy = accessPolicy;
        this.skillMapping = skillMapping;
    }

    @Transactional
    public QuestionResponse create(CreateQuestionRequest request, CurrentUser caller) {
        Visibility visibility = parseVisibility(request.visibility());
        UUID tenantId = resolveTenant(visibility, caller);

        Question question = new Question();
        question.setPteTaskType(parseTaskType(request.pteTaskType()));
        question.setVisibility(visibility);
        question.setTenantId(tenantId);
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
        return toResponse(questionRepository.save(question));
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(UUID publicId, CurrentUser caller) {
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (!accessPolicy.canRead(question.getTenantId(), question.isShared(), caller)) {
            throw new QuestionNotFoundException();
        }
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller) {
        List<Question> questions = caller.isPlatformUser()
                ? questionRepository.findAllWithOptions()
                : questionRepository.findAccessible(caller.tenantId());
        return questions.stream().map(this::toResponse).toList();
    }

    private void addOptions(Question question, List<OptionRequest> options) {
        if (options == null) {
            return;
        }
        options.forEach(source -> {
            QuestionOption option = new QuestionOption();
            option.setText(source.text());
            option.setCorrect(source.correct());
            option.setOrderIndex(source.orderIndex());
            question.addOption(option);
        });
    }

    private UUID resolveTenant(Visibility visibility, CurrentUser caller) {
        if (visibility == Visibility.SHARED) {
            if (!accessPolicy.canWriteShared(caller)) {
                throw new SharedWriteForbiddenException();
            }
            return null;
        }
        if (caller.tenantId() == null) {
            throw new QuestionValidationException(AuthoringConstants.PRIVATE_REQUIRES_TENANT);
        }
        return caller.tenantId();
    }

    private QuestionResponse toResponse(Question question) {
        List<String> skills = skillMapping.skillsFor(question.getPteTaskType()).stream()
                .map(Skill::name).toList();
        return QuestionMapper.toResponse(question, skills);
    }

    private Visibility parseVisibility(String value) {
        try {
            return Visibility.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(AuthoringConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private PteTaskType parseTaskType(String value) {
        try {
            return PteTaskType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(AuthoringConstants.UNKNOWN_TASK_TYPE);
        }
    }
}
