package com.pte.media.internal.controller;

import com.pte.media.internal.dto.request.CloudinaryCompleteRequest;
import com.pte.media.internal.dto.request.CloudinaryUploadRequest;
import com.pte.media.internal.dto.response.CloudinaryUploadResponse;
import com.pte.media.internal.dto.response.MediaPreviewResponse;
import com.pte.media.internal.service.CloudinaryMediaService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Any authenticated actor can request an upload (students recording Read Aloud; hosts uploading media for authoring later). Ownership is enforced at complete-time. */
@RestController
@RequestMapping("/api/v1/objects")
public class MediaController {

    private final CloudinaryMediaService cloudinaryMediaService;

    public MediaController(CloudinaryMediaService cloudinaryMediaService) {
        this.cloudinaryMediaService = cloudinaryMediaService;
    }

    @PostMapping
    public ApiResponse<CloudinaryUploadResponse> requestUpload(@Valid @RequestBody CloudinaryUploadRequest request) {
        return ApiResponse.success(cloudinaryMediaService.requestUpload(request, currentUser()));
    }

    @PostMapping("/{publicId}/complete")
    public ApiResponse<Void> completeUpload(@PathVariable UUID publicId,
            @Valid @RequestBody CloudinaryCompleteRequest request) {
        cloudinaryMediaService.completeUpload(publicId, request, currentUser());
        return ApiResponse.success(null);
    }

    @GetMapping("/{publicId}/preview-url")
    public ApiResponse<MediaPreviewResponse> preview(@PathVariable UUID publicId) {
        return ApiResponse.success(cloudinaryMediaService.preview(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
