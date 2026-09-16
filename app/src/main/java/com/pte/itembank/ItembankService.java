package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.Skill;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.config.PteTaskTypeSkillMapping;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.exception.SharedWriteForbiddenException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code itembank}. {@code
 * QuestionRepository} stays in {@code internal/} — {@code assessment} freezes
 * a question into its own {@code SnapshotItem} through {@link #freeze}, never
 * through the repository directly (Phase 05 Design Constraints).
 *
 * <p>Question authoring + lookup with SHARED/PRIVATE visibility enforcement
 * (ADR-001). A host writes only its own PRIVATE items; only a platform user
 * writes SHARED. Reads return SHARED plus the caller's own PRIVATE.
 */
@Service
public class ItembankService {

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final ItembankAccessPolicy accessPolicy;
    private final PteTaskTypeSkillMapping skillMapping;

    public ItembankService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                           ItembankAccessPolicy accessPolicy, PteTaskTypeSkillMapping skillMapping) {
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

    /** Also used by {@code assessment} to confirm a blueprint item's question exists and is readable by the caller — the returned value is discarded there. */
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

    /** Count platform SHARED questions for a template-feasibility check. */
    @Transactional(readOnly = true)
    public Map<PteTaskType, Long> countSharedByTaskTypes(Set<PteTaskType> taskTypes) {
        if (taskTypes == null || taskTypes.isEmpty()) {
            return Map.of();
        }
        Map<PteTaskType, Long> counts = new HashMap<>();
        questionRepository.countByVisibilityAndTaskTypeIn(Visibility.SHARED, taskTypes)
                .forEach(row -> counts.put((PteTaskType) row[0], ((Number) row[1]).longValue()));
        return Map.copyOf(counts);
    }

    /**
     * Trusted application call — no {@link CurrentUser}/visibility check, matching
     * the pre-split behavior: a question only reaches a blueprint (and so becomes
     * freezable) after {@link #get} already gated it at blueprint-build time.
     * Options come back already in delivery order — rotated one position for
     * {@code RE_ORDER_PARAGRAPHS} so the natural (already-ascending) authored
     * order isn't served pre-solved to the student.
     */
    @Transactional(readOnly = true)
    public QuestionFreezeView freeze(UUID questionPublicId) {
        Question question = questionRepository.findWithOptionsByPublicId(questionPublicId)
                .orElseThrow(QuestionNotFoundException::new);
        List<QuestionFreezeView.Option> options = deliveryOrder(question).stream()
                .map(o -> new QuestionFreezeView.Option(
                        o.getText(), o.isCorrect(), o.getOrderIndex(), o.getBlankIndex(), o.getCorrectGapIndex()))
                .toList();
        return new QuestionFreezeView(
                question.getPublicId(),
                question.getPteTaskType(),
                question.getTitle(),
                question.getPromptText(),
                question.getAudioPromptRef(),
                question.getImagePromptRef(),
                question.getReferenceAnswerText(),
                question.getCorrectAnswerText(),
                question.getMinWordCount(),
                question.getMaxWordCount(),
                options);
    }

    /**
     * {@code Question.options} is JPA-mapped {@code @OrderBy("orderIndex ASC")},
     * so {@code question.getOptions()} always returns options already sorted by
     * their {@code orderIndex} identity — correct for every options-based type
     * where {@code orderIndex} only needs to be a stable choice identity (MC
     * types, the shared fill-blanks word bank). But {@code RE_ORDER_PARAGRAPHS}
     * specifically needs {@code orderIndex} to also be the CORRECT final
     * position, with students shown a shuffled arrangement to rearrange back —
     * serving the natural (already-ascending) order would deliver every
     * paragraph already correctly placed, making the task trivially solved
     * without any rearranging. A fixed single-position rotation (guaranteed to
     * move every option to a different array index whenever there are 2+
     * options) breaks that alignment deterministically.
     */
    List<QuestionOption> deliveryOrder(Question question) {
        List<QuestionOption> natural = question.getOptions();
        if (question.getPteTaskType() != PteTaskType.RE_ORDER_PARAGRAPHS || natural.size() < 2) {
            return natural;
        }
        List<QuestionOption> rotated = new ArrayList<>(natural);
        Collections.rotate(rotated, 1);
        return rotated;
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
            throw new QuestionValidationException(ItembankConstants.PRIVATE_REQUIRES_TENANT);
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
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private PteTaskType parseTaskType(String value) {
        try {
            return PteTaskType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }
}
