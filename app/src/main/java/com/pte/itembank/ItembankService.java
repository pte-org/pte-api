package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.repository.TaskTypeCountProjection;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only door other modules use to reach {@code itembank}. {@code
 * QuestionRepository} stays in {@code internal/} — {@code assessment} freezes
 * a question into its own {@code SnapshotItem} through {@link #freeze}, never
 * through the repository directly (Phase 05 Design Constraints).
 *
 * <p>Platform-owned question authoring and lookup. Every question is SHARED;
 * only platform users may write the bank, while generation reads APPROVED items.
 */
@Service
public class ItembankService {

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final ItembankAccessPolicy accessPolicy;

    public ItembankService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                           ItembankAccessPolicy accessPolicy) {
        this.questionRepository = questionRepository;
        this.validationHelper = validationHelper;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public QuestionResponse create(CreateQuestionRequest request, CurrentUser caller) {
        if (!accessPolicy.canWrite(caller)) {
            throw new AccessDeniedException("Only platform users may write the shared question bank");
        }

        Question question = new Question();
        question.setPteTaskType(parseTaskType(request.pteTaskType()));
        question.setVisibility(Visibility.SHARED);
        question.setTenantId(null);
        question.setStatus(QuestionStatus.APPROVED);
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
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller) {
        List<Question> questions = questionRepository.findAllWithOptions();
        return questions.stream().map(this::toResponse).toList();
    }

    /** Publish DRAFT→APPROVED, validating required fields first. APPROVED is idempotent; ARCHIVED is rejected. */
    @Transactional
    public QuestionResponse publish(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() == QuestionStatus.APPROVED) {
            return toResponse(question);
        }
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        question.setStatus(QuestionStatus.APPROVED);
        return toResponse(question);
    }

    /** Archive DRAFT/APPROVED→ARCHIVED. ARCHIVED is idempotent. */
    @Transactional
    public QuestionResponse archive(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        question.setStatus(QuestionStatus.ARCHIVED);
        return toResponse(question);
    }

    /** Unarchive ARCHIVED→DRAFT only — must be published again (with validation) to re-enter the pool. */
    @Transactional
    public QuestionResponse unarchive(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() != QuestionStatus.ARCHIVED) {
            throw new InvalidQuestionStatusTransitionException();
        }
        question.setStatus(QuestionStatus.DRAFT);
        return toResponse(question);
    }

    /**
     * Exam generation count, one grouped query, always APPROVED+SHARED — never
     * takes a {@link CurrentUser}: the pool doesn't depend on which host asked.
     * Every requested task type is present in the result, 0 if it has no stock.
     */
    @Transactional(readOnly = true)
    public Map<PteTaskType, Long> countPublishedByTaskTypes(Set<PteTaskType> taskTypes) {
        Set<String> names = taskTypes.stream().map(Enum::name).collect(Collectors.toSet());
        Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
        taskTypes.forEach(taskType -> counts.put(taskType, 0L));
        for (TaskTypeCountProjection row : questionRepository.countPublishedSharedGroupedByTaskType(names)) {
            counts.put(PteTaskType.valueOf(row.getTaskType()), row.getCount());
        }
        return counts;
    }

    /** At most {@code n} random APPROVED+SHARED ids for one task type — never takes a {@link CurrentUser}. */
    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIds(PteTaskType taskType, int n) {
        return questionRepository.randomPublishedSharedIdsByTaskType(taskType.name(), n);
    }

    private Question loadForPlatformWrite(UUID publicId, CurrentUser caller) {
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (!caller.isPlatformUser()) {
            throw new AccessDeniedException("Only platform users may write the shared question bank");
        }
        return question;
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
        if (question.getVisibility() != Visibility.SHARED || question.getStatus() != QuestionStatus.APPROVED) {
            throw new QuestionNotFoundException();
        }
        return toFreezeView(question);
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

    private QuestionResponse toResponse(Question question) {
        return QuestionMapper.toResponse(question);
    }

    private PteTaskType parseTaskType(String value) {
        try {
            return PteTaskType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }

    private QuestionFreezeView toFreezeView(Question question) {
        List<QuestionFreezeView.Option> options = deliveryOrder(question).stream()
                .map(o -> new QuestionFreezeView.Option(
                        o.getText(), o.isCorrect(), o.getOrderIndex(), o.getBlankIndex(), o.getCorrectGapIndex()))
                .toList();
        return new QuestionFreezeView(
                question.getPublicId(), question.getPteTaskType(), question.getTitle(), question.getPromptText(),
                question.getAudioPromptRef(), question.getImagePromptRef(), question.getReferenceAnswerText(),
                question.getCorrectAnswerText(), question.getMinWordCount(), question.getMaxWordCount(), options);
    }
}
