package com.pte.session.internal.service.attendance;

import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryItemView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.internal.constant.AttendanceConstants;
import com.pte.session.internal.exception.attendance.AttendanceReceiptNotFoundException;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Reads back attendance commands: a receipt from the idempotency store, the history from the audit trail. */
@Service
public class AttendanceReceiptService {

    private final AttendanceAccess access;
    private final AttendanceReceiptStore receipts;
    private final AttendanceSnapshotFactory snapshots;
    private final ExamRegistrationRepository registrationRepository;
    private final AuditHistoryReader historyReader;
    private final Clock clock;

    AttendanceReceiptService(AttendanceAccess access, AttendanceReceiptStore receipts,
            AttendanceSnapshotFactory snapshots, ExamRegistrationRepository registrationRepository,
            AuditHistoryReader historyReader, Clock clock) {
        this.access = access;
        this.receipts = receipts;
        this.snapshots = snapshots;
        this.registrationRepository = registrationRepository;
        this.historyReader = historyReader;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttendanceCommandReceiptView getReceipt(UUID sessionPublicId, UUID requestPublicId, CurrentUser caller,
            boolean isHost) {
        UUID tenantId = caller.tenantId();
        // The reader is authorized before any lookup, so a receipt never leaks to a non-assigned proctor.
        access.requireHostOrAssignedProctor(sessionPublicId, caller, isHost);
        StoredAttendanceReceipt receipt = receipts.find(AttendanceActionKind.ATTENDANCE, tenantId, sessionPublicId,
                        caller.userId(), requestPublicId, StoredAttendanceReceipt.class)
                .orElseThrow(() -> new AttendanceReceiptNotFoundException(
                        AttendanceConstants.MSG_RECEIPT_NOT_FOUND.formatted(requestPublicId)));
        access.readSession(sessionPublicId, tenantId);
        return AttendanceCommandService.view(receipt, snapshots.current(receipt.studentPublicId(),
                registrationRepository.findActiveEnrollment(sessionPublicId, receipt.studentPublicId(), tenantId)
                        .orElse(null)));
    }

    @Transactional(readOnly = true)
    public AttendanceHistoryView getHistory(UUID sessionPublicId, UUID studentPublicId, int requestedPage,
            int requestedSize, CurrentUser caller, boolean isHost) {
        UUID tenantId = caller.tenantId();
        access.requireHostOrAssignedProctor(sessionPublicId, caller, isHost);
        PagedResult<com.pte.shared.audit.AuditHistoryEntry> page = historyReader.history(tenantId,
                AttendanceConstants.AUDIT_AGGREGATE_ATTENDANCE,
                AttendanceCommandService.auditId(sessionPublicId, studentPublicId),
                AttendanceConstants.AUDIT_ACTION_ATTENDANCE, requestedPage, requestedSize);
        var items = page.data().stream().map(entry -> {
            AttendanceChangeState before = receipts.readState(entry.beforeStateJson(), AttendanceChangeState.class);
            AttendanceChangeState after = receipts.readState(entry.afterStateJson(), AttendanceChangeState.class);
            return new AttendanceHistoryItemView(entry.publicId(), entry.requestPublicId(), studentPublicId,
                    after.registrationPublicId(), entry.actorUserId(), null, after.actorRole(), after.version(),
                    before.status(), after.status(), after.reasonOrNote(), entry.createdAt());
        }).toList();
        return new AttendanceHistoryView(sessionPublicId, studentPublicId, items, page.meta(), clock.instant());
    }
}
