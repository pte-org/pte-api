package com.pte.tenancy.internal.controller;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.tenancy.internal.dto.response.LocationOptionResponse;
import com.pte.tenancy.internal.service.VietnamLocationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Host-scoped location catalog for organization profile forms. */
@RestController
@RequestMapping("/api/v1/locations/vietnam")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostLocationController {

    private final VietnamLocationService vietnamLocationService;

    public HostLocationController(VietnamLocationService vietnamLocationService) {
        this.vietnamLocationService = vietnamLocationService;
    }

    @GetMapping("/provinces")
    public ApiResponse<List<LocationOptionResponse>> provinces() {
        currentUser();
        return ApiResponse.success(vietnamLocationService.listProvinces());
    }

    @GetMapping("/provinces/{provinceCode}/wards")
    public ApiResponse<List<LocationOptionResponse>> wards(@PathVariable String provinceCode) {
        currentUser();
        return ApiResponse.success(vietnamLocationService.listWards(provinceCode));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
