package com.pte.session.internal.service;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.IdentityVerificationStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.request.RecordIdentityVerificationRequest;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.internal.constant.AttendanceConstants;
import com.pte.session.internal.exception.AttendanceConflictException;
import com.pte.session.internal.exception.AttendanceReasonRequiredException;
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Records the identity check of a registered student on their {@link ExamRegistration}. */
@Service
public class IdentityVerificationService {

    private final AttendanceAccess access;
    private final AttendanceWindowPolicy windowPolicy;
    private final AttendanceReceiptStore receipts;
    private final AttendanceSnapshotFactory snapshots;
    private final ExamRegistrationRepository registrationRepository;
    private final AuditLogChangeWriter auditWriter;
    private final Clock clock;

    IdentityVerificationService(AttendanceAccess access, AttendanceWindowPolicy windowPolicy,
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
    public AttendanceRowView recordIdentityVerification(UUID sessionPublicId, UUID studentPublicId,
            RecordIdentityVerificationRequest request, CurrentUser caller, boolean isHost) {
        UUID tenantId = caller.tenantId();
        ExamSession session = access.lockSession(sessionPublicId, tenantId);
        Instant now = clock.instant();
        IdentityVerificationStatus newStatus = requireValid(request);
        access.requireHostOrAssignedProctor(sessionPublicId, caller, isHost);

        String note = request.note() != null && !request.note().isBlank() ? request.note().trim() : null;
        var replay = receipts.replayOrBegin(AttendanceActionKind.IDENTITY_VERIFICATION, session, caller.userId(),
                request.requestPublicId(), fingerprint(studentPublicId, request, newStatus),
                StoredIdentityReceipt.class);
        if (replay.isPresent()) {
            return snapshots.current(studentPublicId, registrationRepository
                    .findActiveEnrollment(sessionPublicId, studentPublicId, tenantId).orElse(null));
        }

        windowPolicy.requireWritable(session, isHost, now, AttendanceActionKind.IDENTITY_VERIFICATION);
        if (isHost && session.getStatus() == SessionStatus.CLOSED && note == null) {
            throw new AttendanceReasonRequiredException(AttendanceConstants.MSG_HOST_REASON_IDENTITY);
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
            throw new AttendanceConflictException(AttendanceConstants.MSG_IDENTITY_VERSION_CONFLICT
                    .formatted(request.expectedVersion(), currentVersion));
        }

        IdentityVerificationStatus previous = registration.getIdentityStatus();
        boolean changed = newStatus != previous || !Objects.equals(note, registration.getIdentityNote());
        String actorRole = isHost ? AttendanceConstants.ACTOR_ROLE_HOST : AttendanceConstants.ACTOR_ROLE_PROCTOR;
        if (changed) {
            String before = receipts.audit(identityState(registration));
            registration.setIdentityStatus(newStatus);
            registration.setIdentityNote(note);
            registration.setLastActorPublicId(caller.userId());
            registration.setLastActorRole(actorRole);
            registration.setLastActionAt(now);
            registration.setAttendanceVersion(currentVersion + 1);
            registrationRepository.save(registration);
            auditWriter.record(caller, new AuditChange(AttendanceConstants.AUDIT_AGGREGATE_ATTENDANCE,
                    AttendanceCommandService.auditId(sessionPublicId, studentPublicId),
                    AttendanceConstants.AUDIT_ACTION_IDENTITY,
                    AttendanceConstants.AUDIT_SUMMARY_IDENTITY.formatted(previous, newStatus),
                    request.requestPublicId(), before, receipts.audit(identityState(registration))));
        }
        receipts.complete(AttendanceActionKind.IDENTITY_VERIFICATION, session, caller.userId(),
                request.requestPublicId(), new StoredIdentityReceipt(changed));
        return snapshots.current(studentPublicId, registration);
    }

    private static IdentityState identityState(ExamRegistration registration) {
        return new IdentityState(registration.getStudentPublicId(), registration.getPublicId(),
                registration.getIdentityStatus(), registration.getIdentityNote(), registration.getAttendanceVersion());
    }

    private static IdentityVerificationStatus requireValid(RecordIdentityVerificationRequest request) {
        if (request.requestPublicId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AttendanceConstants.MSG_REQUEST_ID_REQUIRED);
        }
        if (request.expectedVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    AttendanceConstants.MSG_EXPECTED_VERSION_REQUIRED);
        }
        if (request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AttendanceConstants.MSG_STATUS_REQUIRED);
        }
        try {
            return IdentityVerificationStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    AttendanceConstants.MSG_IDENTITY_STATUS_INVALID);
        }
    }

    private static String fingerprint(UUID studentPublicId, RecordIdentityVerificationRequest request,
            IdentityVerificationStatus status) {
        return AttendanceReceiptStore.fingerprint(String.join(AttendanceConstants.KEY_SEPARATOR,
                studentPublicId.toString(), String.valueOf(request.enrollmentPublicId()),
                String.valueOf(request.expectedVersion()), status.name(),
                request.note() == null ? "" : request.note().trim()));
    }

    /** Before/after snapshot written to the audit trail for one identity change. */
    record IdentityState(java.util.UUID studentPublicId, java.util.UUID registrationPublicId,
            IdentityVerificationStatus status, String note, long version) {
    }

    /** The only fact a retry needs: the snapshot it returns is read fresh. */
    record StoredIdentityReceipt(boolean changed) {
    }
}
