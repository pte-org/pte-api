package com.pte.examstaff.internal;

import com.pte.identity.dto.response.ExamStaffAccountView;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
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
    @GetMapping("/sessions")
    public ApiResponse<?> sessions(@PathVariable UUID staffPublicId, @RequestParam StaffSessionRole role,
            @ModelAttribute StaffWorkspaceQuery query) {
        CurrentUser caller = CurrentUserContext.required();
        return role == StaffSessionRole.EXAMINER
                ? ApiResponse.success(service.examiner(staffPublicId, query, caller))
                : ApiResponse.success(service.proctor(staffPublicId, query, caller));
    }
}
