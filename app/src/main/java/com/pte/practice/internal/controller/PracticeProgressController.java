package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.response.PracticeProgressResponse;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.practice.internal.service.PracticeProgressService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Student-scoped, read-only practice history boundary. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_PROGRESS_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeProgressController {

    private final PracticeProgressService progressService;
    private final PracticeEntitlementService entitlementService;

    public PracticeProgressController(PracticeProgressService progressService,
            PracticeEntitlementService entitlementService) {
        this.progressService = progressService;
        this.entitlementService = entitlementService;
    }

    /** The history is the student's own work, so a student practice is locked for no longer reads it. */
    @GetMapping
    public ApiResponse<PracticeProgressResponse> progress() {
        CurrentUser caller = CurrentUserContext.required();
        entitlementService.assertUnlocked(caller, caller.tenantId());
        return ApiResponse.success(progressService.getProgress(caller.userId()));
    }
}
