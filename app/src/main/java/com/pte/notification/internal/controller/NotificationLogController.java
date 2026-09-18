package com.pte.notification.internal.controller;

import com.pte.notification.internal.dto.response.NotificationLogResponse;
import com.pte.notification.internal.service.NotificationLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class NotificationLogController {

    private final NotificationLogService notificationLogService;

    public NotificationLogController(NotificationLogService notificationLogService) {
        this.notificationLogService = notificationLogService;
    }

    @GetMapping
    public ApiResponse<List<NotificationLogResponse>> list() {
        return ApiResponse.success(notificationLogService.list(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
