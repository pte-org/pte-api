package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.response.PracticeEntitlementResponse;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Protected student-scoped entitlement read model. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_ENTITLEMENT_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeEntitlementController {

    private final PracticeEntitlementService entitlementService;

    public PracticeEntitlementController(PracticeEntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping
    public ApiResponse<PracticeEntitlementResponse> entitlement(
            @RequestParam(required = false) UUID organizationId) {
        return ApiResponse.success(entitlementService.getEntitlement(
                CurrentUserContext.required().userId(), organizationId));
    }
}
