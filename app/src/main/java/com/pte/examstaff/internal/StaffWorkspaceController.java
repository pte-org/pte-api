package com.pte.examstaff.internal;

import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.session.dto.response.StaffSessionView;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-staff/{staffPublicId}")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class StaffWorkspaceController {
    private final StaffWorkspaceService service;
    public StaffWorkspaceController(StaffWorkspaceService service) { this.service = service; }
    @GetMapping
    public ApiResponse<ExamStaffAccountView> detail(@PathVariable UUID staffPublicId) {
        return ApiResponse.success(service.detail(staffPublicId, CurrentUserContext.required()));
    }
    @GetMapping("/overview")
    public ApiResponse<StaffOverviewView> overview(@PathVariable UUID staffPublicId, @ModelAttribute StaffWorkspaceQuery query) {
        return ApiResponse.success(service.overview(staffPublicId, query, CurrentUserContext.required()));
    }
    @GetMapping("/proctor-sessions")
    public ApiResponse<PagedResult<StaffSessionView>> proctor(@PathVariable UUID staffPublicId, @ModelAttribute StaffWorkspaceQuery query) {
        return ApiResponse.success(service.proctor(staffPublicId, query, CurrentUserContext.required()));
    }
    @GetMapping("/examiner-sessions")
    public ApiResponse<ExaminerSessionPage> examiner(@PathVariable UUID staffPublicId, @ModelAttribute StaffWorkspaceQuery query) {
        return ApiResponse.success(service.examiner(staffPublicId, query, CurrentUserContext.required()));
    }
}
