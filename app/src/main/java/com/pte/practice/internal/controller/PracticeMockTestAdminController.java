package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeMockTestGenerateRequest;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestResponse;
import com.pte.practice.internal.service.mocktest.PracticeMockTestService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Builds mock tests. They are shared by every organization, so only a platform admin may create them.
 * {@code generate} is a custom action on the collection, hence the colon path.
 */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_ADMIN_PATH)
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeMockTestAdminController {

    private final PracticeMockTestService mockTestService;

    public PracticeMockTestAdminController(PracticeMockTestService mockTestService) {
        this.mockTestService = mockTestService;
    }

    @GetMapping("/mock-tests")
    public ApiResponse<List<PracticeMockTestResponse>> list() {
        return ApiResponse.success(mockTestService.listForAdmin());
    }

    @PostMapping("/mock-tests:generate")
    public ApiResponse<List<PracticeMockTestResponse>> generate(
            @Valid @RequestBody PracticeMockTestGenerateRequest request) {
        return ApiResponse.success(mockTestService.generate(request.count()));
    }
}
