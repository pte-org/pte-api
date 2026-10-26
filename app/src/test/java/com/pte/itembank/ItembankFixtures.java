package com.pte.itembank;

import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.internal.service.ItembankAccessPolicy;
import com.pte.itembank.internal.service.QuestionPoolService;
import com.pte.itembank.internal.service.QuestionQueryService;
import com.pte.itembank.internal.service.QuestionMediaReferenceValidator;
import com.pte.itembank.internal.service.QuestionValidationHelper;
import com.pte.itembank.internal.service.lifecycle.QuestionApprovalService;
import com.pte.itembank.internal.service.lifecycle.QuestionCreationService;
import com.pte.itembank.internal.service.lifecycle.QuestionLifecycleService;
import com.pte.itembank.internal.service.tasktype.RuntimeContractResolver;
import com.pte.itembank.internal.service.tasktype.RuntimeProfileResolver;
import com.pte.itembank.internal.service.tasktype.TaskTypeCapabilityService;
import com.pte.itembank.internal.service.tasktype.TaskTypeQueryService;
import com.pte.itembank.internal.service.tasktype.TaskTypeRuntimeLockingService;
import com.pte.itembank.internal.service.tasktype.lifecycle.DynamicTaskTypeService;
import com.pte.itembank.internal.service.tasktype.lifecycle.StandardTaskTypeService;
import com.pte.itembank.internal.service.tasktype.lifecycle.TaskTypeGovernanceService;

/** Wires the itembank facades from their collaborators by hand, for focused unit tests. */
public final class ItembankFixtures {

    private ItembankFixtures() {
    }

    public static ItembankService itembankService(QuestionRepository questionRepository,
            QuestionValidationHelper validationHelper, ItembankAccessPolicy accessPolicy) {
        QuestionMediaReferenceValidator mediaValidator = new QuestionMediaReferenceValidator(null);
        QuestionPoolService poolService = new QuestionPoolService(questionRepository, null);
        QuestionApprovalService approvalService = new QuestionApprovalService(questionRepository, validationHelper,
                accessPolicy, null, mediaValidator);
        return new ItembankService(
                new QuestionCreationService(questionRepository, validationHelper, accessPolicy, null, null,
                        mediaValidator),
                new QuestionLifecycleService(questionRepository, validationHelper, accessPolicy, null,
                        approvalService, mediaValidator),
                new QuestionQueryService(questionRepository, poolService));
    }

    public static QuestionTypeService questionTypeService(QuestionTypeRepository repository) {
        TaskRuntimeContractService contractService = new TaskRuntimeContractService();
        RuntimeProfileResolver profileResolver = new RuntimeProfileResolver(null);
        RuntimeContractResolver contractResolver = new RuntimeContractResolver(contractService);
        return new QuestionTypeService(
                new TaskTypeQueryService(repository, profileResolver, contractResolver, null),
                new TaskTypeCapabilityService(repository, contractService),
                new StandardTaskTypeService(repository, profileResolver, null),
                new DynamicTaskTypeService(repository, contractResolver, null, null),
                new TaskTypeGovernanceService(repository, contractResolver, null),
                new TaskTypeRuntimeLockingService(repository));
    }
}
