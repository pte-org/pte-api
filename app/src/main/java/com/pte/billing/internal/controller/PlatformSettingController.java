package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.PlatformSettingRequest;
import com.pte.billing.internal.dto.response.PlatformSettingResponse;
import com.pte.billing.internal.service.PlatformSettingService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform-wide configuration, restricted to platform administrators. */
@RestController
@RequestMapping("/admin/settings")
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

    @GetMapping("/{key}")
    public ApiResponse<PlatformSettingResponse> get(@PathVariable String key) {
        return ApiResponse.success(platformSettingService.get(key));
    }

    @PutMapping("/{key}")
    public ApiResponse<PlatformSettingResponse> update(@PathVariable String key,
            @Valid @RequestBody PlatformSettingRequest request) {
        return ApiResponse.success(platformSettingService.update(key, request));
    }
}
