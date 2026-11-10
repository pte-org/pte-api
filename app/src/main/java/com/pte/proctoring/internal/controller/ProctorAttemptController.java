package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.dto.request.AttemptStatusActionRequest;
import com.pte.proctoring.internal.dto.response.AttemptActionResultResponse;
import com.pte.proctoring.internal.service.ProctorCommandDispatchService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Proctor actions on a Student's attempt in an assigned, open exam session. */
@RestController
@RequestMapping("/api/v1/exam-sessions")
@PreAuthorize("hasRole('PROCTOR')")
public class ProctorAttemptController {

    private final ProctorCommandDispatchService commandService;

    public ProctorAttemptController(ProctorCommandDispatchService commandService) {
        this.commandService = commandService;
    }

    @PatchMapping("/{sessionPublicId}/attempts/{attemptPublicId}/status")
    public ApiResponse<AttemptActionResultResponse> changeStatus(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @Valid @RequestBody AttemptStatusActionRequest request) {
        // FORCE_SUBMIT is the only ProctorCommandType, so the action needs no dispatch yet.
        return ApiResponse.success(commandService.forceSubmit(sessionPublicId, attemptPublicId,
                CurrentUserContext.required()));
    }
}
