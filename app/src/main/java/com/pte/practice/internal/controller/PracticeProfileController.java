package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeProfileUpdateRequest;
import com.pte.practice.internal.dto.response.profile.PracticeProfileResponse;
import com.pte.practice.internal.service.PracticeProfileService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Student-scoped practice profile endpoints (e.g. learning goals and exam date). */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_PROFILE_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeProfileController {

    private final PracticeProfileService profileService;

    public PracticeProfileController(PracticeProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<PracticeProfileResponse> getProfile() {
        return ApiResponse.success(profileService.get(CurrentUserContext.required()));
    }

    @PutMapping
    public ApiResponse<PracticeProfileResponse> updateProfile(
            @Valid @RequestBody(required = false) PracticeProfileUpdateRequest request) {
        return ApiResponse.success(profileService.update(CurrentUserContext.required(), request));
    }
}
