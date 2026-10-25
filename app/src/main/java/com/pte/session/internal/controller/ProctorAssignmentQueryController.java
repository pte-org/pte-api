package com.pte.session.internal.controller;

import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignedSessionsPageView;
import com.pte.session.dto.response.ProctorWorkspaceOverviewView;
import com.pte.session.internal.dto.response.proctor.AssignedProctorSessionResponse;
import com.pte.session.internal.dto.response.proctor.ProctorAssignedSessionsPageResponse;
import com.pte.session.internal.dto.response.proctor.ProctorWorkspaceOverviewResponse;
import com.pte.session.internal.dto.response.proctor.ProctorWorkspaceSessionResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
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

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final SessionService sessionService;

    public ProctorAssignmentQueryController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/mine/overview")
    public ApiResponse<ProctorWorkspaceOverviewResponse> getMyOverview(
            @RequestParam(required = false) String timezone) {
        CurrentUser caller = CurrentUserContext.required();
        ProctorWorkspaceOverviewView view = sessionService
                .getProctorWorkspaceOverview(caller.userId(), caller.tenantId(), timezone);

        List<ProctorWorkspaceSessionResponse> priority = view.prioritySessions().stream()
                .map(v -> new ProctorWorkspaceSessionResponse(
                        v.assignmentPublicId(),
                        v.examSessionPublicId(),
                        v.name(),
                        v.sessionCode(),
                        v.status(),
                        v.opensAt(),
                        v.closesAt(),
                        v.assignmentRole(),
                        v.canMonitor(),
                        v.monitoringBlockingReason()))
                .toList();

        return ApiResponse.success(new ProctorWorkspaceOverviewResponse(
                view.asOf(),
                view.timezone(),
                view.dayStart(),
                view.dayEnd(),
                view.counts(),
                priority));
    }

    @GetMapping("/mine")
    public ApiResponse<ProctorAssignedSessionsPageResponse> listMyAssignedSessions(
            @RequestParam(required = false, defaultValue = "ALL") String scope,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        CurrentUser caller = CurrentUserContext.required();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        ProctorAssignedSessionsPageView pagedView = sessionService
                .getAssignedSessionsForProctor(caller.userId(), caller.tenantId(), scope, timezone, sort, safePage, safeSize);

        List<AssignedProctorSessionResponse> mapped = pagedView.data().stream()
                .map(v -> new AssignedProctorSessionResponse(
                        v.assignmentPublicId(),
                        v.examSessionPublicId(),
                        v.name(),
                        v.sessionCode(),
                        v.status(),
                        v.opensAt(),
                        v.closesAt(),
                        v.assignmentRole(),
                        v.canMonitor(),
                        v.monitoringBlockingReason()))
                .toList();

        return ApiResponse.success(new ProctorAssignedSessionsPageResponse(
                mapped,
                pagedView.meta(),
                pagedView.asOf(),
                pagedView.timezone(),
                pagedView.dayStart(),
                pagedView.dayEnd()));
    }
}
