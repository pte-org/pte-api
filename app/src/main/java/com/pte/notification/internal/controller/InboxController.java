package com.pte.notification.internal.controller;

import com.pte.notification.internal.dto.request.InboxReadAllRequest;
import com.pte.notification.internal.dto.response.InboxItemResponse;
import com.pte.notification.internal.dto.response.InboxPageResponse;
import com.pte.notification.internal.dto.response.InboxReadAllResponse;
import com.pte.notification.internal.dto.response.InboxUnreadCountResponse;
import com.pte.notification.internal.service.InboxReadService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notification-inbox")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOST_ADMIN')")
public class InboxController {
    private final InboxReadService service;

    public InboxController(InboxReadService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<InboxPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String snapshot) {
        return ApiResponse.success(service.list(currentUser(), page, size, filter, category, snapshot));
    }

    @GetMapping("/unread-count")
    public ApiResponse<InboxUnreadCountResponse> unreadCount() {
        return ApiResponse.success(service.unreadCount(currentUser()));
    }

    @GetMapping("/{itemPublicId}")
    public ApiResponse<InboxItemResponse> get(@PathVariable UUID itemPublicId) {
        return ApiResponse.success(service.get(itemPublicId, currentUser()));
    }

    @PutMapping("/{itemPublicId}/read")
    public ApiResponse<InboxItemResponse> markRead(@PathVariable UUID itemPublicId) {
        return ApiResponse.success(service.markRead(itemPublicId, currentUser()));
    }

    @PostMapping("/read-all")
    public ApiResponse<InboxReadAllResponse> markAllRead(
            @RequestBody(required = false) InboxReadAllRequest request) {
        return ApiResponse.success(service.markAllRead(currentUser(), request == null ? null : request.snapshotToken()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
