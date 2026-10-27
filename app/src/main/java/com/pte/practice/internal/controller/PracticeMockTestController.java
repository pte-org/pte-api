package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestResponse;
import com.pte.practice.internal.service.mocktest.PracticeMockTestService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The full mock tests a student can take, with their latest attempt at each. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_MOCK_TEST_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeMockTestController {

    private final PracticeMockTestService mockTestService;

    public PracticeMockTestController(PracticeMockTestService mockTestService) {
        this.mockTestService = mockTestService;
    }

    @GetMapping
    public ApiResponse<List<PracticeMockTestResponse>> list() {
        // Tests are built from the practice bank on demand; opening the list is what keeps them in step with it.
        mockTestService.ensureCapacity();
        return ApiResponse.success(mockTestService.listForStudent(CurrentUserContext.required().userId()));
    }
}
