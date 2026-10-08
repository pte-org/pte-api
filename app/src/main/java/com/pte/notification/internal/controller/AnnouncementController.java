package com.pte.notification.internal.controller;

import com.pte.notification.internal.dto.request.AnnouncementCreateRequest;
import com.pte.notification.internal.dto.request.AnnouncementDeleteRequest;
import com.pte.notification.internal.dto.request.AnnouncementPublishRequest;
import com.pte.notification.internal.dto.request.AnnouncementUpdateRequest;
import com.pte.notification.internal.dto.response.AnnouncementAudiencePreviewResponse;
import com.pte.notification.internal.dto.response.AnnouncementResponse;
import com.pte.notification.internal.service.AnnouncementService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/announcements")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class AnnouncementController {
    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PagedResult<AnnouncementResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(currentUser(), page, size));
    }

    @PostMapping
    public ApiResponse<AnnouncementResponse> create(@RequestBody AnnouncementCreateRequest request) {
        return ApiResponse.success(service.create(currentUser(), request));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<AnnouncementResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.get(currentUser(), publicId));
    }

    @PatchMapping("/{publicId}")
    public ApiResponse<AnnouncementResponse> update(@PathVariable UUID publicId,
            @RequestBody AnnouncementUpdateRequest request) {
        return ApiResponse.success(service.update(currentUser(), publicId, request));
    }

    @DeleteMapping("/{publicId}")
    public ApiResponse<Void> delete(@PathVariable UUID publicId,
            @RequestBody(required = false) AnnouncementDeleteRequest request) {
        service.delete(currentUser(), publicId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{publicId}/audience-preview")
    public ApiResponse<AnnouncementAudiencePreviewResponse> preview(@PathVariable UUID publicId) {
        return ApiResponse.success(service.preview(currentUser(), publicId));
    }

    @PostMapping("/{publicId}/publish")
    public ApiResponse<AnnouncementResponse> publish(@PathVariable UUID publicId,
            @RequestBody AnnouncementPublishRequest request) {
        return ApiResponse.success(service.publish(currentUser(), publicId, request));
    }

    @PostMapping("/{publicId}/retry-delivery")
    public ApiResponse<AnnouncementResponse> retry(@PathVariable UUID publicId) {
        return ApiResponse.success(service.retryDelivery(currentUser(), publicId));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
