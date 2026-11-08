package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.prompt.version.AiPromptVersionCommandService;
import com.pte.itembank.internal.generation.prompt.version.AiPromptVersionQueryService;
import com.pte.itembank.internal.generation.prompt.version.PromptTestService;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptProposalRequest;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptStatusRequest;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptTestResponse;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptVersionResponse;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptWorkspaceResponse;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Editing the AI prompts: the page of one prompt, a proposal for changing it, a test run and the approval flow. Writers, managers and
 * admins may use it; what each of them may do to a version is decided by the services.
 */
@RestController
@RequestMapping(PromptVersionConstants.API_BASE_PATH)
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class AiPromptVersionController {

    private final AiPromptVersionCommandService commands;
    private final AiPromptVersionQueryService queries;
    private final PromptTestService tests;

    public AiPromptVersionController(AiPromptVersionCommandService commands, AiPromptVersionQueryService queries,
                                     PromptTestService tests) {
        this.commands = commands;
        this.queries = queries;
        this.tests = tests;
    }

    @GetMapping("/prompts/{category}/{key}/workspace")
    public ApiResponse<PromptWorkspaceResponse> workspace(@PathVariable String category, @PathVariable String key) {
        return ApiResponse.success(queries.workspace(category, key));
    }

    @PutMapping("/prompts/{category}/{key}/proposal")
    public ApiResponse<PromptVersionResponse> saveProposal(@PathVariable String category, @PathVariable String key,
                                                           @Valid @RequestBody PromptProposalRequest request) {
        return ApiResponse.success(commands.saveProposal(category, key, request, CurrentUserContext.required()));
    }

    @GetMapping("/prompt-versions/{publicId}")
    public ApiResponse<PromptVersionResponse> version(@PathVariable UUID publicId) {
        return ApiResponse.success(queries.get(publicId));
    }

    @PostMapping("/prompt-versions/{publicId}/test")
    public ApiResponse<PromptTestResponse> test(@PathVariable UUID publicId) {
        return ApiResponse.success(tests.run(publicId, CurrentUserContext.required()));
    }

    @PatchMapping("/prompt-versions/{publicId}/status")
    public ApiResponse<PromptVersionResponse> status(@PathVariable UUID publicId,
                                                     @Valid @RequestBody PromptStatusRequest request) {
        return ApiResponse.success(commands.changeStatus(publicId, request, CurrentUserContext.required()));
    }
}
