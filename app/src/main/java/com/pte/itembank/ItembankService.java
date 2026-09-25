package com.pte.itembank;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.dto.request.RejectQuestionRequest;
import com.pte.itembank.dto.request.UpdateQuestionRequest;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.dto.response.QuestionStatsResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.InvalidQuestionStatusTransitionException;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.exception.QuestionVersionConflictException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.repository.TaskTypeCountProjection;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.media.MediaService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionRepository questionRepository;
    private final QuestionValidationHelper validationHelper;
    private final ItembankAccessPolicy accessPolicy;
    private final MediaService mediaService;
    private final QuestionTypeService questionTypeService;

    @Autowired
    public ItembankService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
                           ItembankAccessPolicy accessPolicy, MediaService mediaService,
                           QuestionTypeService questionTypeService) {
        this.questionRepository = questionRepository;
        this.validationHelper = validationHelper;
        this.accessPolicy = accessPolicy;
        this.mediaService = mediaService;
        this.questionTypeService = questionTypeService;
    }

    /** Compatibility constructor for focused itembank unit tests without media wiring. */
    public ItembankService(QuestionRepository questionRepository, QuestionValidationHelper validationHelper,
            ItembankAccessPolicy accessPolicy) {
        this(questionRepository, validationHelper, accessPolicy, null, null);
    }

    @Transactional
    public QuestionResponse create(CreateQuestionRequest request, CurrentUser caller) {
        if (!accessPolicy.canWrite(caller)) {
            throw new AccessDeniedException("Only platform users may write the shared question bank");
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
        question.setTenantId(null);
        question.setStatus(QuestionStatus.DRAFT);
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
        validateMediaReferences(question, caller);
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

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller, String taskType, String status, String query) {
        return listAccessible(caller, taskType, null, status, query);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller, String taskType, String section, String status,
            String query) {
        String requestedTaskTypeKey = parseOptionalTaskTypeKey(taskType);
        PteSection requestedSection = parseOptionalSection(section);
        QuestionStatus requestedStatus = parseOptionalStatus(status);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        return questionRepository.findAllWithOptions().stream()
                .filter(question -> requestedTaskTypeKey == null || requestedTaskTypeKey.equals(
                        question.getTaskTypeKey() == null && question.getPteTaskType() != null
                                ? question.getPteTaskType().name() : question.getTaskTypeKey()))
                .filter(question -> requestedSection == null || requestedSection.name().equals(
                        question.getTaskTypeSection() == null && question.getPteTaskType() != null
                                ? question.getPteTaskType().getSection().name() : question.getTaskTypeSection()))
                .filter(question -> requestedStatus == null || question.getStatus() == requestedStatus)
                .filter(question -> normalizedQuery.isBlank()
                        || question.getTitle().toLowerCase().contains(normalizedQuery)
                        || (question.getPromptText() != null
                                && question.getPromptText().toLowerCase().contains(normalizedQuery)))
                .map(this::toResponse)
                .toList();
    }

    /** Server-side question-bank search used by the common paginated UI. */
    @Transactional(readOnly = true)
    public PagedResult<QuestionResponse> listAccessible(CurrentUser caller, int requestedPage, int requestedSize,
            String taskType, String section, String status, String query) {
        int page = normalizePage(requestedPage);
        int size = normalizePageSize(requestedSize);
        String requestedTaskTypeKey = parseOptionalTaskTypeKey(taskType);
        PteSection requestedSection = parseOptionalSection(section);
        QuestionStatus requestedStatus = parseOptionalStatus(status);
        String normalizedQuery = normalizeQuery(query);
        UUID publicIdQuery = parsePublicIdQuery(query);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<UUID> questionPage = questionRepository.findPagePublicIds(
                requestedTaskTypeKey,
                requestedSection == null ? null : requestedSection.name(),
                requestedStatus,
                normalizedQuery,
                publicIdQuery,
                pageable);

        List<UUID> publicIds = questionPage.getContent();
        Map<UUID, Question> questionsByPublicId = publicIds.isEmpty()
                ? Map.of()
                : questionRepository.findWithOptionsByPublicIdIn(publicIds).stream()
                        .collect(Collectors.toMap(Question::getPublicId, question -> question,
                                (first, ignored) -> first, HashMap::new));
        List<QuestionResponse> responses = publicIds.stream()
                .map(questionsByPublicId::get)
                .filter(question -> question != null)
                .map(this::toResponse)
                .toList();

        return new PagedResult<>(responses,
                new PageMeta(questionPage.getNumber(), questionPage.getSize(), questionPage.getTotalElements(),
                        questionPage.getTotalPages(), questionPage.isFirst(), questionPage.isLast(),
                        questionPage.hasNext(), questionPage.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public QuestionStatsResponse stats(CurrentUser caller) {
        Map<String, Long> sectionCounts = questionRepository.countBySectionAndVisibility(Visibility.SHARED).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).longValue()));
        Map<QuestionStatus, Long> statusCounts = questionRepository.countByStatusAndVisibility(Visibility.SHARED)
                .stream()
                .collect(Collectors.toMap(row -> (QuestionStatus) row[0], row -> ((Number) row[1]).longValue()));
        return new QuestionStatsResponse(
                questionRepository.countByDeletedFalseAndVisibility(Visibility.SHARED),
                sectionCounts.getOrDefault(PteSection.LISTENING.name(), 0L),
                sectionCounts.getOrDefault(PteSection.READING.name(), 0L),
                sectionCounts.getOrDefault(PteSection.WRITING.name(), 0L),
                sectionCounts.getOrDefault(PteSection.SPEAKING.name(), 0L),
                statusCounts.getOrDefault(QuestionStatus.DRAFT, 0L));
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
        validationHelper.validate(question);
        validateMediaReferences(question, caller);
        return toResponse(question);
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
        revision.setTenantId(source.getTenantId());
        revision.setStatus(QuestionStatus.DRAFT);
        revision.setRevisionGroupPublicId(source.getRevisionGroupPublicId());
        revision.setRevisionNumber(source.getRevisionNumber() + 1);
        revision.setSupersedesPublicId(source.getPublicId());
        revision.setCurrent(false);
        applyContent(revision, source.getTitle(), source.getPromptText(), source.getAudioPromptRef(),
                source.getImagePromptRef(), source.getReferenceAnswerText(), source.getCorrectAnswerText(),
                source.getMinWordCount(), source.getMaxWordCount(), source.getOptions().stream()
                        .map(option -> new OptionRequest(option.getText(), option.isCorrect(), option.getOrderIndex(),
                                option.getBlankIndex(), option.getCorrectGapIndex()))
                        .toList());
        return toResponse(questionRepository.save(revision));
    }

    @Transactional
    public QuestionResponse submitApproval(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        validateMediaReferences(question, caller);
        question.setRejectionReason(null);
        question.setStatus(QuestionStatus.PENDING_APPROVAL);
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse approve(UUID publicId, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            throw new AccessDeniedException("Only platform admins may approve questions");
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (question.getStatus() != QuestionStatus.PENDING_APPROVAL) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        validateMediaReferences(question, caller);
        archiveSupersededQuestion(question);
        question.setRejectionReason(null);
        question.setCurrent(true);
        question.setStatus(QuestionStatus.APPROVED);
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse reject(UUID publicId, RejectQuestionRequest request, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            throw new AccessDeniedException("Only platform admins may reject questions");
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (question.getStatus() != QuestionStatus.PENDING_APPROVAL) {
            throw new InvalidQuestionStatusTransitionException();
        }
        question.setStatus(QuestionStatus.DRAFT);
        question.setRejectionReason(request.reason());
        return toResponse(question);
    }

    /** Publish DRAFT→APPROVED, validating required fields first. APPROVED is idempotent; ARCHIVED is rejected. */
    @Transactional
    public QuestionResponse publish(UUID publicId, CurrentUser caller) {
        if (!accessPolicy.canApprove(caller)) {
            throw new AccessDeniedException("Only platform admins may publish questions");
        }
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        if (question.getStatus() == QuestionStatus.APPROVED) {
            return toResponse(question);
        }
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new InvalidQuestionStatusTransitionException();
        }
        validationHelper.validate(question);
        validateMediaReferences(question, caller);
        archiveSupersededQuestion(question);
        question.setRejectionReason(null);
        question.setCurrent(true);
        question.setStatus(QuestionStatus.APPROVED);
        return toResponse(question);
    }

    private void archiveSupersededQuestion(Question question) {
        if (question.getSupersedesPublicId() == null) {
            return;
        }
        questionRepository.findWithOptionsByPublicId(question.getSupersedesPublicId()).ifPresent(previous -> {
            previous.setCurrent(false);
            previous.setStatus(QuestionStatus.ARCHIVED);
            // Flush now so the previous revision's is_current=false UPDATE reaches the
            // database before this question's is_current=true UPDATE is flushed — otherwise
            // Hibernate may order the two statements the other way around (it was loaded
            // into the persistence context first) and both rows momentarily hold
            // is_current=true, violating uq_questions_current_revision_group.
            questionRepository.flush();
        });
    }

    /** Archive DRAFT/APPROVED→ARCHIVED. ARCHIVED is idempotent. */
    @Transactional
    public QuestionResponse archive(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        question.setStatus(QuestionStatus.ARCHIVED);
        question.setCurrent(false);
        return toResponse(question);
    }

    /** Unarchive ARCHIVED→DRAFT only — must be published again (with validation) to re-enter the pool. */
    @Transactional
    public QuestionResponse unarchive(UUID publicId, CurrentUser caller) {
        Question question = loadForPlatformWrite(publicId, caller);
        if (question.getStatus() != QuestionStatus.ARCHIVED) {
            throw new InvalidQuestionStatusTransitionException();
        }
        if (question.getSupersedesPublicId() != null) {
            throw new InvalidQuestionStatusTransitionException();
        }
        question.setStatus(QuestionStatus.DRAFT);
        // A later revision may already hold the group's one "current" slot (e.g. this
        // question was archived after being superseded, not archived directly) — only
        // reclaim it when nothing else in the group has it, or uq_questions_current_revision_group
        // rejects the update.
        boolean groupHasCurrentElsewhere = questionRepository
                .existsByRevisionGroupPublicIdAndCurrentTrueAndPublicIdNot(
                        question.getRevisionGroupPublicId(), question.getPublicId());
        question.setCurrent(!groupHasCurrentElsewhere);
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
            counts.put(TaskTypeCodeCompatibility.parse(row.getTaskType()), row.getCount());
        }
        return counts;
    }

    /** At most {@code n} random APPROVED+SHARED ids for one task type — never takes a {@link CurrentUser}. */
    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIds(PteTaskType taskType, int n) {
        return questionRepository.randomPublishedSharedIdsByTaskType(taskType.name(), n);
    }

    /** Stable candidate list used by deterministic exam generation. */
    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIds(PteTaskType taskType) {
        return questionRepository.publishedSharedIdsByTaskType(taskType.name());
    }

    /** Logical-key generation facade used by custom templates. */
    @Transactional(readOnly = true)
    public Map<String, Long> countPublishedByTaskTypeKeys(Set<String> taskTypeKeys) {
        Set<String> normalized = taskTypeKeys.stream()
                .map(this::normalizeTaskTypeKey)
                .collect(Collectors.toSet());
        Map<String, Long> counts = normalized.stream()
                .collect(Collectors.toMap(key -> key, key -> 0L, (left, right) -> left, java.util.LinkedHashMap::new));
        for (TaskTypeCountProjection row : questionRepository.countPublishedSharedGroupedByTaskTypeKey(normalized)) {
            counts.put(row.getTaskType(), row.getCount());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIdsByTaskTypeKey(String taskTypeKey, int n) {
        return questionRepository.randomPublishedSharedIdsByTaskTypeKey(normalizeTaskTypeKey(taskTypeKey), n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIdsByTaskTypeKey(String taskTypeKey) {
        return questionRepository.publishedSharedIdsByTaskTypeKey(normalizeTaskTypeKey(taskTypeKey));
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
        if (question.getVisibility() != Visibility.SHARED || question.getStatus() != QuestionStatus.APPROVED
                || !question.isCurrent()) {
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
        boolean usesOptionOrderAsCorrectPosition = questionTypeService == null
                ? question.getPteTaskType() == PteTaskType.RE_ORDER_PARAGRAPHS
                : questionTypeService.findDefinitionByCode(question.getTaskTypeKey() == null
                        && question.getPteTaskType() != null ? question.getPteTaskType().name()
                                : question.getTaskTypeKey())
                        .map(definition -> definition.isUsesOptionOrderAsCorrectPosition())
                        .orElse(false);
        if (!usesOptionOrderAsCorrectPosition || natural.size() < 2) {
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
            option.setBlankIndex(source.blankIndex());
            option.setCorrectGapIndex(source.correctGapIndex());
            question.addOption(option);
        });
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

    private void validateMediaReferences(Question question, CurrentUser caller) {
        if (mediaService == null) {
            return;
        }
        if (question.getAudioPromptRef() != null) {
            mediaService.validateAuthoringMedia(question.getAudioPromptRef(), "AUDIO_PROMPT", caller);
        }
        if (question.getImagePromptRef() != null) {
            mediaService.validateAuthoringMedia(question.getImagePromptRef(), "IMAGE_PROMPT", caller);
        }
    }

    private int normalizePage(int requestedPage) {
        return Math.max(DEFAULT_PAGE, requestedPage);
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private UUID parsePublicIdQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(query.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String parseOptionalTaskTypeKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TaskTypeCodeCompatibility.normalizeTaskTypeKey(value);
        } catch (RuntimeException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }

    private QuestionStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuestionStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private PteSection parseOptionalSection(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PteSection.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private QuestionResponse toResponse(Question question) {
        return QuestionMapper.toResponse(question);
    }

    private PteTaskType parseTaskType(String value) {
        try {
            return TaskTypeCodeCompatibility.parse(value);
        } catch (RuntimeException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }

    private QuestionFreezeView toFreezeView(Question question) {
        List<QuestionFreezeView.Option> options = deliveryOrder(question).stream()
                .map(o -> new QuestionFreezeView.Option(
                        o.getText(), o.isCorrect(), o.getOrderIndex(), o.getBlankIndex(), o.getCorrectGapIndex()))
                .toList();
        String taskTypeKey = question.getTaskTypeKey() == null && question.getPteTaskType() != null
                ? question.getPteTaskType().name() : question.getTaskTypeKey();
        String displayName = questionTypeService == null ? taskTypeKey
                : questionTypeService.findDefinitionByCode(taskTypeKey)
                        .map(definition -> definition.getDisplayName())
                        .orElse(taskTypeKey);
        return new QuestionFreezeView(
                question.getPublicId(), question.getPteTaskType(), question.getTitle(), question.getPromptText(),
                question.getAudioPromptRef(), question.getImagePromptRef(), question.getReferenceAnswerText(),
                question.getCorrectAnswerText(), question.getMinWordCount(), question.getMaxWordCount(), options,
                taskTypeKey,
                question.getTaskTypeSection() == null && question.getPteTaskType() != null
                        ? question.getPteTaskType().getSection().name() : question.getTaskTypeSection(), null,
                displayName);
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

    private String normalizeTaskTypeKey(String raw) {
        try {
            return TaskTypeCodeCompatibility.normalizeTaskTypeKey(raw);
        } catch (RuntimeException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }
}
