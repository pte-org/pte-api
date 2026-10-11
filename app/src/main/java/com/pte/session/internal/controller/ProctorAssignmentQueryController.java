package com.pte.session.internal.controller;

import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.session.internal.dto.response.AssignedProctorSessionResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PROCTOR-only assigned sessions endpoint for the tenant web proctor workspace.
 */
@RestController
@RequestMapping("/api/v1/proctor-assignments")
@PreAuthorize("hasRole('PROCTOR')")
public class ProctorAssignmentQueryController {

    private final SessionService sessionService;

    public ProctorAssignmentQueryController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/mine")
    public ApiResponse<PagedResult<AssignedProctorSessionResponse>> listMyAssignedSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        CurrentUser caller = CurrentUserContext.required();
        PagedResult<ProctorMonitoringSessionView> pagedResult = sessionService
                .getAssignedSessionsForProctor(caller.userId(), caller.tenantId(), page, size);

        List<AssignedProctorSessionResponse> mapped = pagedResult.data().stream()
                .map(v -> new AssignedProctorSessionResponse(
                        v.assignmentPublicId(),
                        v.examSessionPublicId(),
                        v.name(),
                        v.sessionCode(),
                        v.status(),
                        v.opensAt(),
                        v.closesAt(),
                        v.assignmentRole()))
                .toList();

        return ApiResponse.success(new PagedResult<>(mapped, pagedResult.meta()));
    }
}
