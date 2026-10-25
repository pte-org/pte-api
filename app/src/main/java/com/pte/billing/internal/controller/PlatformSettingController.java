package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.PlatformSettingBatchRequest;
import com.pte.billing.internal.dto.response.PlatformSettingResponse;
import com.pte.billing.internal.service.PlatformSettingService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform-wide configuration, restricted to platform administrators. */
@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformSettingController {

    private final PlatformSettingService platformSettingService;

    public PlatformSettingController(PlatformSettingService platformSettingService) {
        this.platformSettingService = platformSettingService;
    }

    @GetMapping
    public ApiResponse<List<PlatformSettingResponse>> list() {
        return ApiResponse.success(platformSettingService.list());
    }

    @PutMapping("/batch")
    public ApiResponse<List<PlatformSettingResponse>> updateBatch(
            @Valid @RequestBody PlatformSettingBatchRequest request) {
        return ApiResponse.success(platformSettingService.updateBatch(request, CurrentUserContext.required()));
    }

}
