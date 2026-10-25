package com.pte.session.internal.service;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.internal.constant.AttendanceConstants;
import com.pte.session.internal.exception.AttendanceConflictException;
import com.pte.session.internal.exception.AttendanceReasonRequiredException;
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a student's attendance on their {@link ExamRegistration}. A retry with the same request id replays
 * the stored receipt; the history of changed commands is the audit trail of the registration.
 */
@Service
public class AttendanceCommandService {

    private final AttendanceAccess access;
    private final AttendanceWindowPolicy windowPolicy;
    private final AttendanceReceiptStore receipts;
    private final AttendanceSnapshotFactory snapshots;
    private final ExamRegistrationRepository registrationRepository;
    private final AuditLogChangeWriter auditWriter;
    private final Clock clock;

    AttendanceCommandService(AttendanceAccess access, AttendanceWindowPolicy windowPolicy,
            AttendanceReceiptStore receipts, AttendanceSnapshotFactory snapshots,
            ExamRegistrationRepository registrationRepository, AuditLogChangeWriter auditWriter, Clock clock) {
        this.access = access;
        this.windowPolicy = windowPolicy;
        this.receipts = receipts;
        this.snapshots = snapshots;
        this.registrationRepository = registrationRepository;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional
    public AttendanceCommandReceiptView recordAttendance(UUID sessionPublicId, UUID studentPublicId,
            RecordAttendanceRequest request, CurrentUser caller, boolean isHost) {
        UUID tenantId = caller.tenantId();
        ExamSession session = access.lockSession(sessionPublicId, tenantId);
        // The clock is read after the lock so a cutoff decision is never stale.
        Instant now = clock.instant();
        access.requireHostOrAssignedProctor(sessionPublicId, caller, isHost);

        var replay = receipts.replayOrBegin(AttendanceActionKind.ATTENDANCE, session, caller.userId(),
                request.requestPublicId(), fingerprint(studentPublicId, request), StoredAttendanceReceipt.class);
        if (replay.isPresent()) {
            return view(replay.get(), currentOf(sessionPublicId, studentPublicId, tenantId));
        }

        windowPolicy.requireWritable(session, isHost, now, AttendanceActionKind.ATTENDANCE);
        String note = canonicalOrNull(request.reasonOrNote());
        if (isHost && note == null) {
            throw new AttendanceReasonRequiredException(AttendanceConstants.MSG_HOST_REASON_ATTENDANCE);
        }
        ExamRegistration registration = registrationRepository
                .findActiveEnrollment(sessionPublicId, studentPublicId, tenantId)
                .orElseThrow(EnrollmentNotFoundException::new);
        if (!registration.getPublicId().equals(request.enrollmentPublicId())) {
            throw new AttendanceConflictException(AttendanceConstants.MSG_GENERATION_MISMATCH
                    .formatted(request.enrollmentPublicId(), registration.getPublicId()));
        }
        long currentVersion = registration.getAttendanceVersion();
        if (!request.expectedVersion().equals(currentVersion)) {
            throw new AttendanceConflictException(AttendanceConstants.MSG_ATTENDANCE_VERSION_CONFLICT
                    .formatted(request.expectedVersion(), currentVersion));
        }
        AttendanceStatus previous = registration.getAttendanceStatus();
        if (!isHost && previous != AttendanceStatus.UNMARKED && request.status() != previous && note == null) {
            throw new AttendanceReasonRequiredException(AttendanceConstants.MSG_PROCTOR_REASON_CHANGE);
        }

        String actorRole = isHost ? AttendanceConstants.ACTOR_ROLE_HOST : AttendanceConstants.ACTOR_ROLE_PROCTOR;
        boolean changed = request.status() != previous;
        StoredAttendanceReceipt receipt = new StoredAttendanceReceipt(request.requestPublicId(), sessionPublicId,
                studentPublicId, registration.getPublicId(), caller.userId(), actorRole,
                changed ? currentVersion + 1 : currentVersion, previous, request.status(), note, now, changed,
                changed ? AttendanceConstants.RESULT_SUCCESS : AttendanceConstants.RESULT_NO_OP);
        if (changed) {
            applyChange(registration, request, note, caller, actorRole, now);
        }
        receipts.complete(AttendanceActionKind.ATTENDANCE, session, caller.userId(), request.requestPublicId(),
                receipt);
        return view(receipt, snapshots.current(studentPublicId, registration));
    }

    private void applyChange(ExamRegistration registration, RecordAttendanceRequest request, String note,
            CurrentUser caller, String actorRole, Instant now) {
        UUID studentPublicId = registration.getStudentPublicId();
        AttendanceChangeState before = state(registration, actorRole);
        registration.setAttendanceStatus(request.status());
        registration.setAttendanceVersion(registration.getAttendanceVersion() + 1);
        registration.setLastActorPublicId(caller.userId());
        registration.setLastActorRole(actorRole);
        registration.setLastActionAt(now);
        registration.setLastReasonOrNote(note);
        registrationRepository.save(registration);
        auditWriter.record(caller, new AuditChange(AttendanceConstants.AUDIT_AGGREGATE_ATTENDANCE,
                auditId(registration.getSession().getPublicId(), studentPublicId),
                AttendanceConstants.AUDIT_ACTION_ATTENDANCE,
                AttendanceConstants.AUDIT_SUMMARY_ATTENDANCE.formatted(before.status(), request.status()),
                request.requestPublicId(), receipts.audit(before), receipts.audit(state(registration, actorRole))));
    }

    private AttendanceRowView currentOf(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
        return snapshots.current(studentPublicId, registrationRepository
                .findActiveEnrollment(sessionPublicId, studentPublicId, tenantId).orElse(null));
    }

    static AttendanceCommandReceiptView view(StoredAttendanceReceipt receipt, AttendanceRowView currentAttendance) {
        return new AttendanceCommandReceiptView(receipt.requestPublicId(), receipt.sessionPublicId(),
                receipt.studentPublicId(), receipt.registrationPublicId(), receipt.actorPublicId(),
                receipt.actorRole(), receipt.appliedVersion(), receipt.previousStatus(), receipt.newStatus(),
                receipt.reasonOrNote(), receipt.serverActionAt(), receipt.changed(), receipt.committedResult(),
                currentAttendance);
    }

    static String auditId(UUID sessionPublicId, UUID studentPublicId) {
        return sessionPublicId + AttendanceConstants.AUDIT_ID_SEPARATOR + studentPublicId;
    }

    private static AttendanceChangeState state(ExamRegistration registration, String actorRole) {
        return new AttendanceChangeState(registration.getStudentPublicId(), registration.getPublicId(),
                registration.getAttendanceStatus(), registration.getAttendanceVersion(),
                registration.getLastReasonOrNote(), actorRole);
    }

    private static String fingerprint(UUID studentPublicId, RecordAttendanceRequest request) {
        return AttendanceReceiptStore.fingerprint(String.join(AttendanceConstants.KEY_SEPARATOR,
                studentPublicId.toString(), String.valueOf(request.enrollmentPublicId()),
                String.valueOf(request.expectedVersion()), String.valueOf(request.status()),
                request.reasonOrNote() == null ? "" : request.reasonOrNote().trim()));
    }

    private static String canonicalOrNull(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
