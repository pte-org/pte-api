package com.pte.scoring.internal.rubric.controller;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.dto.RubricVersionResponse;
import com.pte.scoring.internal.rubric.service.RubricVersionCommandService;
import com.pte.scoring.internal.rubric.workflow.RubricDiffService;
import com.pte.scoring.internal.rubric.workflow.RubricAuditQuery;
import com.pte.scoring.internal.rubric.workflow.dto.RubricActivationAuditResponse;
import com.pte.scoring.internal.rubric.workflow.dto.RubricDiffResponse;
import com.pte.scoring.internal.rubric.workflow.dto.RubricStatusRequest;
import com.pte.scoring.internal.rubric.workflow.dto.RubricWriteRequest;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping(RubricConstants.RUBRIC_API_PATH + "/versions")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class RubricVersionController {
    private final RubricVersionCommandService commands;
    private final RubricDiffService diffs;
    private final RubricAuditQuery audits;

    public RubricVersionController(RubricVersionCommandService commands, RubricDiffService diffs, RubricAuditQuery audits) {
        this.commands = commands;
        this.diffs = diffs;
        this.audits = audits;
    }

    @PostMapping
    public ApiResponse<RubricVersionResponse> create(@Valid @RequestBody RubricWriteRequest request) {
        return ApiResponse.success(commands.createDraft(request, CurrentUserContext.required()));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<RubricVersionResponse> update(@PathVariable UUID publicId, @Valid @RequestBody RubricWriteRequest request) {
        return ApiResponse.success(commands.updateDraft(publicId, request, CurrentUserContext.required()));
    }

    @PatchMapping("/{publicId}/status")
    public ApiResponse<RubricVersionResponse> status(@PathVariable UUID publicId, @Valid @RequestBody RubricStatusRequest request) {
        var caller = CurrentUserContext.required();
        if (request.action() == RubricStatusRequest.Action.ACTIVATE) {
            caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER);
            return ApiResponse.success(commands.activate(publicId, request, caller));
        }
        if (request.action() == RubricStatusRequest.Action.REJECT)
            caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER);
        return ApiResponse.success(commands.changeStatus(publicId, request, caller));
    }

    @GetMapping("/{publicId}/diff")
    public ApiResponse<RubricDiffResponse> diff(@PathVariable UUID publicId) {
        return ApiResponse.success(diffs.read(publicId));
    }

    @GetMapping("/{publicId}/activations")
    public ApiResponse<java.util.List<RubricActivationAuditResponse>> activations(@PathVariable UUID publicId) {
        return ApiResponse.success(audits.forVersion(publicId));
    }
}
