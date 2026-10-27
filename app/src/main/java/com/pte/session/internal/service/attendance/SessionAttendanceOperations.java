package com.pte.session.internal.service.attendance;

import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.request.RecordIdentityVerificationRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.dto.response.AttendancePageView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The attendance surface the public {@code SessionService} facade exposes:
 * roster reads, attendance and identity commands, receipts and history. Each
 * call goes to the owning attendance service unchanged (transactions and
 * locking stay on those services).
 */
@Service
public class SessionAttendanceOperations {

    private final AttendanceQueryService attendanceQueryService;
    private final AttendanceCommandService attendanceCommandService;
    private final IdentityVerificationService identityVerificationService;
    private final AttendanceReceiptService attendanceReceiptService;

    public SessionAttendanceOperations(AttendanceQueryService attendanceQueryService,
            AttendanceCommandService attendanceCommandService,
            IdentityVerificationService identityVerificationService,
            AttendanceReceiptService attendanceReceiptService) {
        this.attendanceQueryService = attendanceQueryService;
        this.attendanceCommandService = attendanceCommandService;
        this.identityVerificationService = identityVerificationService;
        this.attendanceReceiptService = attendanceReceiptService;
    }

    public AttendancePageView getAttendanceRoster(UUID sessionPublicId, String status, String search, int page,
            int size, CurrentUser caller, boolean isHost) {
        return attendanceQueryService.getRosterPage(sessionPublicId, caller.tenantId(), status, search, page, size,
                caller, isHost);
    }

    public AttendanceCommandReceiptView recordAttendance(UUID sessionPublicId, UUID studentPublicId,
            RecordAttendanceRequest request, CurrentUser caller, boolean isHost) {
        return attendanceCommandService.recordAttendance(sessionPublicId, studentPublicId, request, caller, isHost);
    }

    public AttendanceCommandReceiptView getAttendanceReceipt(UUID sessionPublicId, UUID requestPublicId,
            CurrentUser caller, boolean isHost) {
        return attendanceReceiptService.getReceipt(sessionPublicId, requestPublicId, caller, isHost);
    }

    public AttendanceHistoryView getAttendanceHistory(UUID sessionPublicId, UUID studentPublicId, int page, int size,
            CurrentUser caller, boolean isHost) {
        return attendanceReceiptService.getHistory(sessionPublicId, studentPublicId, page, size, caller, isHost);
    }

    public AttendanceRowView recordIdentityVerification(UUID sessionPublicId, UUID studentPublicId,
            RecordIdentityVerificationRequest request, CurrentUser caller, boolean isHost) {
        return identityVerificationService.recordIdentityVerification(sessionPublicId, studentPublicId, request,
                caller, isHost);
    }
}
