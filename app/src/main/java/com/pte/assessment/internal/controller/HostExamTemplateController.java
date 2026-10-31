package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.request.CreateHostExamTemplateRequest;
import com.pte.assessment.dto.request.NormalizeSkillWeightsRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse;
import com.pte.assessment.internal.service.definition.HostExamTemplateService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Score templates owned by the calling host's tenant. Every route resolves templates by the caller's tenant, so a
 * template of another host (or a platform default) behaves as if it did not exist.
 */
@RestController
@RequestMapping("/api/v1/host/exam-templates")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostExamTemplateController {

    private final HostExamTemplateService service;

    @Autowired
    public HostExamTemplateController(HostExamTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ExamDefinitionResponse>> list() {
        return ApiResponse.success(service.list(CurrentUserContext.required()));
    }

    @PostMapping
    public ApiResponse<ExamDefinitionResponse> createFromDefault(
            @Valid @RequestBody CreateHostExamTemplateRequest request) {
        return ApiResponse.success(service.createFromDefault(request, CurrentUserContext.required()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<ExamDefinitionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.get(publicId, CurrentUserContext.required()));
    }

    /** Stateless preview of the 3 redistribution modes for the editor's unsaved rows. */
    @PostMapping("/skill-weight-suggestions")
    public ApiResponse<SkillWeightSuggestionResponse> suggestSkillWeights(
            @Valid @RequestBody NormalizeSkillWeightsRequest request) {
        return ApiResponse.success(service.suggestSkillWeights(request, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/clone")
    public ApiResponse<ExamDefinitionResponse> clone(@PathVariable UUID publicId) {
        return ApiResponse.success(service.cloneToDraft(publicId, CurrentUserContext.required()));
    }

    @PutMapping("/{publicId}/items")
    public ApiResponse<ExamDefinitionResponse> replaceItems(@PathVariable UUID publicId,
            @Valid @RequestBody ReplaceExamDefinitionItemsRequest request) {
        return ApiResponse.success(service.replaceItems(publicId, request, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/activate")
    public ApiResponse<ExamDefinitionResponse> activate(@PathVariable UUID publicId) {
        return ApiResponse.success(service.activate(publicId, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/retire")
    public ApiResponse<ExamDefinitionResponse> retire(@PathVariable UUID publicId) {
        return ApiResponse.success(service.retire(publicId, CurrentUserContext.required()));
    }

    @DeleteMapping("/{publicId}")
    public ApiResponse<Void> delete(@PathVariable UUID publicId) {
        service.deleteDraft(publicId, CurrentUserContext.required());
        return ApiResponse.success(null);
    }
}
