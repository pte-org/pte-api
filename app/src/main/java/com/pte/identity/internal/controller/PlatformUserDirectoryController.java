package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.response.UserDirectoryEntryResponse;
import com.pte.identity.internal.service.PlatformUserService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only platform-user directory used to resolve vendor audit actors. */
@RestController
@RequestMapping("/api/v1/platform-users")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class PlatformUserDirectoryController {

    private final PlatformUserService platformUserService;

    public PlatformUserDirectoryController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @GetMapping("/directory")
    public ApiResponse<List<UserDirectoryEntryResponse>> directory() {
        return ApiResponse.success(platformUserService.listDirectory(CurrentUserContext.required()));
    }
}
