package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticePreflightRequest;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticePreflightResponse;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.catalog.PracticeSkillStatsService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Safe catalog and capability-preflight boundary for authenticated students. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_CATALOG_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeCatalogController {

    private final PracticeCatalogService catalogService;
    private final PracticeSkillStatsService skillStatsService;

    public PracticeCatalogController(PracticeCatalogService catalogService,
            PracticeSkillStatsService skillStatsService) {
        this.catalogService = catalogService;
        this.skillStatsService = skillStatsService;
    }

    @GetMapping
    public ApiResponse<PracticeCatalogResponse> catalog() {
        return ApiResponse.success(skillStatsService.withCounts(
                catalogService.getStudentCatalog(), CurrentUserContext.required().userId()));
    }

    @PostMapping("/preflight")
    public ApiResponse<PracticePreflightResponse> preflight(
            @Valid @RequestBody PracticePreflightRequest request) {
        return ApiResponse.success(catalogService.preflight(request));
    }
}
