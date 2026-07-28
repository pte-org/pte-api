package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.response.AssignedProctorSessionResponse;
import com.pte.scheduling.service.ProctorAssignmentQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/proctor-assignments")
@PreAuthorize("hasRole('PROCTOR')")
public class MyProctorAssignmentController {

    private final ProctorAssignmentQueryService service;

    public MyProctorAssignmentController(ProctorAssignmentQueryService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ApiResponse<List<AssignedProctorSessionResponse>> listMine() {
        return ApiResponse.success(service.listMine(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
