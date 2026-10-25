package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.domain.enums.Visibility;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.dto.response.QuestionStatsResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionNotFoundException;
import com.pte.itembank.internal.exception.QuestionValidationException;
import com.pte.itembank.internal.mapper.QuestionMapper;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles question lookup and search (get, list, stats). Published-pool queries delegated to
 * QuestionPoolService. Extracted from ItembankService to keep facade ≤300 lines.
 */
@Service
public class QuestionQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionRepository questionRepository;
    private final QuestionPoolService poolService;

    public QuestionQueryService(QuestionRepository questionRepository, QuestionPoolService poolService) {
        this.questionRepository = questionRepository;
        this.poolService = poolService;
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(UUID publicId, CurrentUser caller) {
        Question question = questionRepository.findWithOptionsByPublicId(publicId)
                .orElseThrow(QuestionNotFoundException::new);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller) {
        return questionRepository.findAllWithOptions().stream().map(this::toResponse).toList();
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
                .filter(q -> requestedTaskTypeKey == null || requestedTaskTypeKey.equals(
                        q.getTaskTypeKey() == null && q.getPteTaskType() != null
                                ? q.getPteTaskType().name() : q.getTaskTypeKey()))
                .filter(q -> requestedSection == null || requestedSection.name().equals(
                        q.getTaskTypeSection() == null && q.getPteTaskType() != null
                                ? q.getPteTaskType().getSection().name() : q.getTaskTypeSection()))
                .filter(q -> requestedStatus == null || q.getStatus() == requestedStatus)
                .filter(q -> normalizedQuery.isBlank()
                        || q.getTitle().toLowerCase().contains(normalizedQuery)
                        || (q.getPromptText() != null && q.getPromptText().toLowerCase().contains(normalizedQuery)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResult<QuestionResponse> listAccessible(CurrentUser caller, int requestedPage, int requestedSize,
            String taskType, String section, String status, String query) {
        return listAccessible(caller, requestedPage, requestedSize, taskType, section, status, query, null);
    }

    @Transactional(readOnly = true)
    public PagedResult<QuestionResponse> listAccessible(CurrentUser caller, int requestedPage, int requestedSize,
            String taskType, String section, String status, String query, String pool) {
        int page = normalizePage(requestedPage);
        int size = normalizePageSize(requestedSize);
        String requestedTaskTypeKey = parseOptionalTaskTypeKey(taskType);
        PteSection requestedSection = parseOptionalSection(section);
        QuestionStatus requestedStatus = parseOptionalStatus(status);
        QuestionPool requestedPool = parseOptionalPool(pool);
        String normalizedQuery = normalizeQuery(query);
        UUID publicIdQuery = parsePublicIdQuery(query);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<UUID> questionPage = questionRepository.findPagePublicIds(
                requestedTaskTypeKey,
                requestedSection == null ? null : requestedSection.name(),
                requestedStatus,
                requestedPool,
                normalizedQuery,
                publicIdQuery,
                pageable);

        List<UUID> publicIds = questionPage.getContent();
        Map<UUID, Question> questionsByPublicId = publicIds.isEmpty()
                ? Map.of()
                : questionRepository.findWithOptionsByPublicIdIn(publicIds).stream()
                        .collect(Collectors.toMap(Question::getPublicId, q -> q,
                                (first, ignored) -> first, HashMap::new));
        List<QuestionResponse> responses = publicIds.stream()
                .map(questionsByPublicId::get)
                .filter(q -> q != null)
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

    // Delegate pool queries to QuestionPoolService
    @Transactional(readOnly = true)
    public Map<PteTaskType, Long> countPublishedByTaskTypes(java.util.Set<PteTaskType> taskTypes) {
        return poolService.countPublishedByTaskTypes(taskTypes);
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIds(PteTaskType taskType, int n) {
        return poolService.randomPublishedQuestionIds(taskType, n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIds(PteTaskType taskType) {
        return poolService.publishedQuestionIds(taskType);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countPublishedByTaskTypeKeys(java.util.Set<String> taskTypeKeys) {
        return poolService.countPublishedByTaskTypeKeys(taskTypeKeys);
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIdsByTaskTypeKey(String taskTypeKey, int n) {
        return poolService.randomPublishedQuestionIdsByTaskTypeKey(taskTypeKey, n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIdsByTaskTypeKey(String taskTypeKey) {
        return poolService.publishedQuestionIdsByTaskTypeKey(taskTypeKey);
    }

    @Transactional(readOnly = true)
    public QuestionFreezeView freeze(UUID questionPublicId) {
        return poolService.freeze(questionPublicId);
    }

    private QuestionResponse toResponse(Question question) {
        return QuestionMapper.toResponse(question);
    }

    private int normalizePage(int requestedPage) {
        return Math.max(DEFAULT_PAGE, requestedPage);
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private UUID parsePublicIdQuery(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            return UUID.fromString(query.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String parseOptionalTaskTypeKey(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.pte.itembank.TaskTypeCodeCompatibility.normalizeTaskTypeKey(value);
        } catch (RuntimeException ex) {
            throw new QuestionValidationException(ItembankConstants.UNKNOWN_TASK_TYPE);
        }
    }

    private QuestionStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return QuestionStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private QuestionPool parseOptionalPool(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return QuestionPool.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }

    private PteSection parseOptionalSection(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return PteSection.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new QuestionValidationException(ItembankConstants.INVALID_QUESTION_FIELDS);
        }
    }
}
