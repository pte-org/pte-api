package com.pte.support.internal.controller;

import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.dto.request.SubmitTicketRequest;
import com.pte.support.internal.dto.response.SupportTicketResponse;
import com.pte.support.internal.dto.response.SupportTicketSummaryResponse;
import com.pte.support.internal.service.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support-tickets")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostSupportTicketController {

    private final SupportTicketService supportTicketService;

    public HostSupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @PostMapping
    public ApiResponse<SupportTicketResponse> submit(@Valid @RequestBody SubmitTicketRequest request) {
        return ApiResponse.success(supportTicketService.submit(request, CurrentUserContext.required()));
    }

    @GetMapping
    public ApiResponse<PagedResult<SupportTicketSummaryResponse>> list(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(supportTicketService.listForHost(status, category, page, size,
                CurrentUserContext.required()));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportTicketResponse> getDetail(@PathVariable UUID id) {
        return ApiResponse.success(supportTicketService.getDetailForHost(id, CurrentUserContext.required()));
    }
}
