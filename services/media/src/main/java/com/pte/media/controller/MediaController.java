package com.pte.media.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.media.dto.request.RequestUploadRequest;
import com.pte.media.dto.response.RequestUploadResponse;
import com.pte.media.service.PresignService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Any authenticated actor can request an upload (students recording Read Aloud; hosts uploading media for authoring later). Ownership is enforced at complete-time. */
@RestController
@RequestMapping("/objects")
public class MediaController {

    private final PresignService presignService;

    public MediaController(PresignService presignService) {
        this.presignService = presignService;
    }

    @PostMapping
    public ApiResponse<RequestUploadResponse> requestUpload(@Valid @RequestBody RequestUploadRequest request) {
        return ApiResponse.success(presignService.requestUpload(request, currentUser()));
    }

    @PostMapping("/{publicId}/complete")
    public ApiResponse<Void> completeUpload(@PathVariable UUID publicId) {
        presignService.completeUpload(publicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
