package com.pte.itembank;

import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.RejectQuestionRequest;
import com.pte.itembank.dto.request.UpdateQuestionRequest;
import com.pte.itembank.dto.response.QuestionFreezeView;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.dto.response.QuestionStatsResponse;
import com.pte.itembank.internal.service.lifecycle.QuestionCreationService;
import com.pte.itembank.internal.service.lifecycle.QuestionLifecycleService;
import com.pte.itembank.internal.service.QuestionPoolService;
import com.pte.itembank.internal.service.QuestionQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code itembank}. Thin facade delegating to:
 * - QuestionCreationService (create)
 * - QuestionLifecycleService (update, archive, unarchive, revisions, approval workflows)
 * - QuestionQueryService (get, list, stats, freeze)
 * - QuestionPoolService (published pool queries for exam generation)
 */
@Service
public class ItembankService {

    private final QuestionCreationService creationService;
    private final QuestionLifecycleService lifecycleService;
    private final QuestionQueryService queryService;

    @Autowired
    public ItembankService(QuestionCreationService creationService, QuestionLifecycleService lifecycleService,
                           QuestionQueryService queryService) {
        this.creationService = creationService;
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
    }

    // Creation
    @Transactional
    public QuestionResponse create(CreateQuestionRequest request, CurrentUser caller) {
        return creationService.create(request, caller);
    }

    // Query/Lookup
    @Transactional(readOnly = true)
    public QuestionResponse get(UUID publicId, CurrentUser caller) {
        return queryService.get(publicId, caller);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller) {
        return queryService.listAccessible(caller);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller, String taskType, String status, String query) {
        return queryService.listAccessible(caller, taskType, status, query);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listAccessible(CurrentUser caller, String taskType, String section, String status,
            String query) {
        return queryService.listAccessible(caller, taskType, section, status, query);
    }

    @Transactional(readOnly = true)
    public PagedResult<QuestionResponse> listAccessible(CurrentUser caller, int requestedPage, int requestedSize,
            String taskType, String section, String status, String query) {
        return queryService.listAccessible(caller, requestedPage, requestedSize, taskType, section, status, query);
    }

    @Transactional(readOnly = true)
    public PagedResult<QuestionResponse> listAccessible(CurrentUser caller, int requestedPage, int requestedSize,
            String taskType, String section, String status, String query, String pool) {
        return queryService.listAccessible(caller, requestedPage, requestedSize, taskType, section, status, query, pool);
    }

    @Transactional(readOnly = true)
    public QuestionStatsResponse stats(CurrentUser caller) {
        return queryService.stats(caller);
    }

    // Lifecycle
    @Transactional
    public QuestionResponse update(UUID publicId, UpdateQuestionRequest request, CurrentUser caller) {
        return lifecycleService.update(publicId, request, caller);
    }

    @Transactional
    public QuestionResponse createRevision(UUID publicId, CurrentUser caller) {
        return lifecycleService.createRevision(publicId, caller);
    }

    @Transactional
    public QuestionResponse submitApproval(UUID publicId, CurrentUser caller) {
        return lifecycleService.submitApproval(publicId, caller);
    }

    @Transactional
    public QuestionResponse approve(UUID publicId, CurrentUser caller) {
        return lifecycleService.approve(publicId, caller);
    }

    @Transactional
    public QuestionResponse reject(UUID publicId, RejectQuestionRequest request, CurrentUser caller) {
        return lifecycleService.reject(publicId, request, caller);
    }

    @Transactional
    public QuestionResponse publish(UUID publicId, CurrentUser caller) {
        return lifecycleService.publish(publicId, caller);
    }

    @Transactional
    public QuestionResponse archive(UUID publicId, CurrentUser caller) {
        return lifecycleService.archive(publicId, caller);
    }

    @Transactional
    public QuestionResponse unarchive(UUID publicId, CurrentUser caller) {
        return lifecycleService.unarchive(publicId, caller);
    }

    // Published pool queries (exam generation)
    @Transactional(readOnly = true)
    public Map<PteTaskType, Long> countPublishedByTaskTypes(Set<PteTaskType> taskTypes) {
        return queryService.countPublishedByTaskTypes(taskTypes);
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIds(PteTaskType taskType, int n) {
        return queryService.randomPublishedQuestionIds(taskType, n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIds(PteTaskType taskType) {
        return queryService.publishedQuestionIds(taskType);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countPublishedByTaskTypeKeys(Set<String> taskTypeKeys) {
        return queryService.countPublishedByTaskTypeKeys(taskTypeKeys);
    }

    @Transactional(readOnly = true)
    public List<UUID> randomPublishedQuestionIdsByTaskTypeKey(String taskTypeKey, int n) {
        return queryService.randomPublishedQuestionIdsByTaskTypeKey(taskTypeKey, n);
    }

    @Transactional(readOnly = true)
    public List<UUID> publishedQuestionIdsByTaskTypeKey(String taskTypeKey) {
        return queryService.publishedQuestionIdsByTaskTypeKey(taskTypeKey);
    }

    @Transactional(readOnly = true)
    public QuestionFreezeView freeze(UUID questionPublicId) {
        return queryService.freeze(questionPublicId);
    }
}
