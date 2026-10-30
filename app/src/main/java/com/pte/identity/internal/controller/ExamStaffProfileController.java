package com.pte.identity.internal.controller;

import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.identity.internal.dto.request.profile.UpdateExamStaffProfileRequest;
import com.pte.identity.internal.service.ExamStaffAccountService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{publicId}/exam-staff-profile")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class ExamStaffProfileController {
    private final ExamStaffAccountService accounts;
    public ExamStaffProfileController(ExamStaffAccountService accounts) { this.accounts = accounts; }
    @PatchMapping
    public ApiResponse<ExamStaffAccountView> update(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateExamStaffProfileRequest request) {
        return ApiResponse.success(accounts.update(publicId, request, CurrentUserContext.required()));
    }
}
