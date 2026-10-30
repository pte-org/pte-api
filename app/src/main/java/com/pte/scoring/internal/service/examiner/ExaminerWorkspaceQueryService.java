package com.pte.scoring.internal.service.examiner;

import com.pte.identity.IdentityService;
import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import com.pte.scoring.dto.response.ExaminerWorkspaceOverviewResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.exception.examiner.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.repository.ExaminerWorkspaceReadRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Service owning Examiner workspace read queries: overview counts, progress, priority preview,
 * and unified queue list with readiness states.
 */
@Service
public class ExaminerWorkspaceQueryService {

    private final ExaminerWorkspaceReadRepository readRepository;
    private final IdentityService identityService;
    private final Clock clock;

    @Autowired
    public ExaminerWorkspaceQueryService(
            ExaminerWorkspaceReadRepository readRepository,
            IdentityService identityService,
            Clock clock) {
        this.readRepository = readRepository;
        this.identityService = identityService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExaminerWorkspaceOverviewResponse getWorkspaceOverview(CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        return readRepository.getWorkspaceOverview(examiner.tenantId(), examiner.userId(), clock.instant());
    }

    @Transactional(readOnly = true)
    public Page<ExaminerQueueItemResponse> findQueue(
            UUID sessionPublicId, String status, Pageable pageable, CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        return readRepository.findQueue(examiner.tenantId(), examiner.userId(), sessionPublicId, status, pageable);
    }

    private CurrentUser requireActiveExaminer(CurrentUser caller) {
        if (caller == null || caller.userId() == null || caller.tenantId() == null
                || !caller.hasRole("EXAMINER")) {
            throw new AccessDeniedException(ExaminerScoringConstants.AUTHENTICATED_TENANT_EXAMINER_REQUIRED);
        }
        if (identityService.findActiveExaminers(caller.tenantId(), List.of(caller.userId())).isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }
        return caller;
    }
}
