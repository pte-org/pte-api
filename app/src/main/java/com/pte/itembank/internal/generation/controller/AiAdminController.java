package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.internal.generation.dto.AiStatusResponse;
import com.pte.itembank.internal.generation.dto.GenerationStatsResponse;
import com.pte.itembank.internal.generation.job.AiOverviewService;
import com.pte.itembank.internal.generation.dto.PromptCatalogResponse;
import com.pte.itembank.internal.generation.job.GenerationStatsService;
import com.pte.itembank.internal.generation.prompt.PromptDocumentService;
import com.pte.shared.web.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Read-only AI administration pages: what is configured, how generation is going, what the prompts say. */
@RestController
@RequestMapping("/api/v1/ai")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AiAdminController {

    /** The prompts are read by everyone who may edit them; the rest of this controller stays admin only. */
    private static final String ACADEMIC_ROLES =
            "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')";

    private final AiOverviewService overview;
    private final GenerationStatsService stats;
    private final PromptDocumentService prompts;

    public AiAdminController(AiOverviewService overview, GenerationStatsService stats,
                             PromptDocumentService prompts) {
        this.overview = overview;
        this.stats = stats;
        this.prompts = prompts;
    }

    @GetMapping("/status")
    public ApiResponse<AiStatusResponse> status() {
        return ApiResponse.success(overview.status());
    }

    @GetMapping("/prompts")
    @PreAuthorize(ACADEMIC_ROLES)
    public ApiResponse<PromptCatalogResponse> promptCatalog() {
        return ApiResponse.success(prompts.catalog());
    }

    @GetMapping("/prompts/{category}/{key}")
    @PreAuthorize(ACADEMIC_ROLES)
    public ApiResponse<PromptDocument> prompt(@PathVariable String category, @PathVariable String key) {
        return ApiResponse.success(prompts.document(category, key));
    }

    @GetMapping("/question-generation/stats")
    public ApiResponse<GenerationStatsResponse> generationStats(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(from != null && to != null ? stats.stats(from, to) : stats.stats(days));
    }
}
