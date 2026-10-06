package com.pte.support.internal.controller;

import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import com.pte.support.domain.enums.TicketCategory;
import com.pte.support.domain.enums.TicketStatus;
import com.pte.support.internal.dto.request.AddNoteRequest;
import com.pte.support.internal.dto.request.UpdateTicketStatusRequest;
import com.pte.support.internal.dto.response.SupportTicketResponse;
import com.pte.support.internal.dto.response.SupportTicketSummaryResponse;
import com.pte.support.internal.service.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    public AdminSupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping
    public ApiResponse<PagedResult<SupportTicketSummaryResponse>> listAll(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(supportTicketService.listAllForAdmin(status, category, tenantId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportTicketResponse> getDetail(@PathVariable UUID id) {
        return ApiResponse.success(supportTicketService.getDetailForAdmin(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<SupportTicketResponse> updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ApiResponse.success(supportTicketService.updateStatus(id, request, CurrentUserContext.required()));
    }

    @PostMapping("/{id}/notes")
    public ApiResponse<SupportTicketResponse> addNote(@PathVariable UUID id,
            @Valid @RequestBody AddNoteRequest request) {
        return ApiResponse.success(supportTicketService.addNote(id, request, CurrentUserContext.required()));
    }
}
