package com.pte.session.internal.service;

import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.SessionAttendance;
import com.pte.session.domain.SessionAttendanceCommand;
import com.pte.session.domain.SessionIdentityVerificationCommand;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.request.RecordIdentityVerificationRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryItemView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.internal.exception.AttendanceConflictException;
import com.pte.session.internal.exception.AttendanceIdempotencyConflictException;
import com.pte.session.internal.exception.AttendanceReasonRequiredException;
import com.pte.session.internal.exception.AttendanceWindowClosedException;
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.session.internal.repository.SessionAttendanceCommandRepository;
import com.pte.session.internal.repository.SessionAttendanceRepository;
import com.pte.session.internal.repository.SessionIdentityVerificationCommandRepository;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AttendanceCommandService {

    private final ExamSessionRepository examSessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SessionAttendanceRepository sessionAttendanceRepository;
    private final SessionAttendanceCommandRepository sessionAttendanceCommandRepository;
    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final SessionIdentityVerificationCommandRepository sessionIdentityVerificationCommandRepository;
    private final Clock clock;

    @Autowired
    public AttendanceCommandService(
            ExamSessionRepository examSessionRepository,
            EnrollmentRepository enrollmentRepository,
            SessionAttendanceRepository sessionAttendanceRepository,
            SessionAttendanceCommandRepository sessionAttendanceCommandRepository,
            ProctorAssignmentRepository proctorAssignmentRepository,
            SessionIdentityVerificationCommandRepository sessionIdentityVerificationCommandRepository,
            Clock clock) {
        this.examSessionRepository = examSessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionAttendanceRepository = sessionAttendanceRepository;
        this.sessionAttendanceCommandRepository = sessionAttendanceCommandRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.sessionIdentityVerificationCommandRepository = sessionIdentityVerificationCommandRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public AttendanceCommandService(
            ExamSessionRepository examSessionRepository,
            EnrollmentRepository enrollmentRepository,
            SessionAttendanceRepository sessionAttendanceRepository,
            SessionAttendanceCommandRepository sessionAttendanceCommandRepository,
            ProctorAssignmentRepository proctorAssignmentRepository) {
        this(examSessionRepository, enrollmentRepository, sessionAttendanceRepository,
                sessionAttendanceCommandRepository, proctorAssignmentRepository, null, Clock.systemUTC());
    }

    @Transactional
    public AttendanceCommandReceiptView recordAttendance(
            UUID sessionPublicId,
            UUID studentPublicId,
            RecordAttendanceRequest request,
            CurrentUser caller,
            boolean isHost) {

        UUID tenantId = caller.tenantId();

        // 1. Lock the session row to serialize concurrent writes
        ExamSession session = examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);

        // R7: Evaluate clock after acquiring the lock to avoid stale cutoff decisions
        Instant now = clock.instant();

        // 2. Authorization check
        if (!isHost) {
            boolean assigned = proctorAssignmentRepository.isProctorAssigned(sessionPublicId, caller.userId(), tenantId);
            if (!assigned) {
                throw new com.pte.session.internal.exception.ProctorNotAssignedException();
            }
        }

        // 3. R5: Idempotent receipt lookup before mutable lifecycle/timing checks
        String fingerprint = computeFingerprint(studentPublicId, request.enrollmentPublicId(),
                request.expectedVersion(), request.status(), request.reasonOrNote());

        Optional<SessionAttendanceCommand> existingCommand = sessionAttendanceCommandRepository
                .findByTenantIdAndSessionIdAndActorPublicIdAndRequestPublicId(
                        tenantId, session.getId(), caller.userId(), request.requestPublicId());

        if (existingCommand.isPresent()) {
            SessionAttendanceCommand saved = existingCommand.get();
            if (!saved.getIntentFingerprint().equals(fingerprint)) {
                throw new AttendanceIdempotencyConflictException("Request ID " + request.requestPublicId()
                        + " already used with a different command intent");
            }
            Enrollment activeEnrollment = enrollmentRepository
                    .findActiveEnrollment(sessionPublicId, studentPublicId, tenantId)
                    .orElse(null);
            AttendanceRowView currentSnapshot = buildCurrentSnapshot(session, activeEnrollment, studentPublicId, now, isHost);
            return toReceiptView(saved, sessionPublicId, currentSnapshot);
        }

        // 4. Lifecycle and window check for new commands
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new AttendanceWindowClosedException("Cannot record attendance for a cancelled session");
        }

        if (isHost) {
            if (session.getStatus() != SessionStatus.SCHEDULED
                    && session.getStatus() != SessionStatus.OPEN
                    && session.getStatus() != SessionStatus.CLOSED) {
                throw new AttendanceWindowClosedException("Host attendance modification is only permitted for SCHEDULED, OPEN, or CLOSED sessions");
            }
            if (request.reasonOrNote() == null || request.reasonOrNote().trim().isBlank()) {
                throw new AttendanceReasonRequiredException("Reason is mandatory for Host attendance actions");
            }
        } else {
            if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.OPEN) {
                throw new AttendanceWindowClosedException("Proctor attendance recording is only allowed for SCHEDULED or OPEN sessions");
            }
            Instant opensAt = session.getOpensAt();
            Instant closesAt = session.getClosesAt();
            if (opensAt == null || closesAt == null) {
                throw new AttendanceWindowClosedException("Session timing is missing");
            }
            Instant windowOpensAt = opensAt.minus(30, ChronoUnit.MINUTES);
            if (now.isBefore(windowOpensAt)) {
                throw new AttendanceWindowClosedException("Attendance recording is not permitted before 30 minutes prior to session opening");
            }
            if (!now.isBefore(closesAt)) {
                throw new AttendanceWindowClosedException("Attendance recording is closed after session closing time");
            }
        }

        // 5. Verify target enrollment
        Enrollment enrollment = enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId)
                .orElseThrow(com.pte.session.internal.exception.EnrollmentNotFoundException::new);

        if (!enrollment.getPublicId().equals(request.enrollmentPublicId())) {
            throw new AttendanceConflictException("Enrollment generation mismatch: request targeted "
                    + request.enrollmentPublicId() + " but active enrollment is " + enrollment.getPublicId());
        }

        // 6. Expected version check (retaining monotonic version across re-enrollments)
        Optional<SessionAttendance> optRow = sessionAttendanceRepository
                .findBySessionIdAndStudentPublicId(session.getId(), studentPublicId);

        Long currentEffectiveVersion = optRow.map(SessionAttendance::getVersion).orElse(0L);
        AttendanceStatus currentEffectiveStatus = AttendanceStatus.UNMARKED;

        if (optRow.isPresent()) {
            SessionAttendance row = optRow.get();
            if (enrollment.getPublicId().equals(row.getEnrollmentPublicId())) {
                currentEffectiveStatus = row.getStatus();
            }
        }

        if (!request.expectedVersion().equals(currentEffectiveVersion)) {
            throw new AttendanceConflictException("Attendance version conflict: expected " + request.expectedVersion()
                    + " but current version is " + currentEffectiveVersion);
        }

        // 7. Status transition reason rules for Proctor
        if (!isHost) {
            if (currentEffectiveStatus != AttendanceStatus.UNMARKED && request.status() != currentEffectiveStatus) {
                if (request.reasonOrNote() == null || request.reasonOrNote().trim().isBlank()) {
                    throw new AttendanceReasonRequiredException("Reason is required when changing marked attendance status or resetting to UNMARKED");
                }
            }
        }

        String canonicalReasonOrNote = request.reasonOrNote() != null && !request.reasonOrNote().trim().isBlank()
                ? request.reasonOrNote().trim()
                : null;

        String actorRole = isHost ? "HOST_ADMIN" : "PROCTOR";

        // 8. No-op execution vs Mutation
        if (request.status() == currentEffectiveStatus) {
            SessionAttendanceCommand cmd = new SessionAttendanceCommand();
            cmd.setTenantId(tenantId);
            cmd.setSession(session);
            cmd.setActorPublicId(caller.userId());
            cmd.setActorRole(actorRole);
            cmd.setRequestPublicId(request.requestPublicId());
            cmd.setIntentFingerprint(fingerprint);
            cmd.setStudentPublicId(studentPublicId);
            cmd.setEnrollmentPublicId(enrollment.getPublicId());
            cmd.setExpectedVersion(currentEffectiveVersion);
            cmd.setAppliedVersion(currentEffectiveVersion);
            cmd.setPreviousStatus(currentEffectiveStatus);
            cmd.setNewStatus(request.status());
            cmd.setReasonOrNote(canonicalReasonOrNote);
            cmd.setServerActionAt(now);
            cmd.setChanged(false);
            cmd.setCommittedResult("NO_OP");

            sessionAttendanceCommandRepository.save(cmd);

            AttendanceRowView currentSnapshot = buildCurrentSnapshot(session, enrollment, studentPublicId, now, isHost);
            return toReceiptView(cmd, sessionPublicId, currentSnapshot);
        }

        Long nextVersion = currentEffectiveVersion + 1;
        SessionAttendance row;
        if (optRow.isPresent()) {
            row = optRow.get();
        } else {
            row = new SessionAttendance();
            row.setSession(session);
            row.setTenantId(tenantId);
            row.setStudentPublicId(studentPublicId);
        }

        row.setEnrollmentPublicId(enrollment.getPublicId());
        row.setStatus(request.status());
        row.setVersion(nextVersion);
        row.setLastActorPublicId(caller.userId());
        row.setLastActorRole(actorRole);
        row.setLastActionAt(now);
        row.setLastReasonOrNote(canonicalReasonOrNote);
        sessionAttendanceRepository.save(row);

        SessionAttendanceCommand cmd = new SessionAttendanceCommand();
        cmd.setTenantId(tenantId);
        cmd.setSession(session);
        cmd.setActorPublicId(caller.userId());
        cmd.setActorRole(actorRole);
        cmd.setRequestPublicId(request.requestPublicId());
        cmd.setIntentFingerprint(fingerprint);
        cmd.setStudentPublicId(studentPublicId);
        cmd.setEnrollmentPublicId(enrollment.getPublicId());
        cmd.setExpectedVersion(currentEffectiveVersion);
        cmd.setAppliedVersion(nextVersion);
        cmd.setPreviousStatus(currentEffectiveStatus);
        cmd.setNewStatus(request.status());
        cmd.setReasonOrNote(canonicalReasonOrNote);
        cmd.setServerActionAt(now);
        cmd.setChanged(true);
        cmd.setCommittedResult("SUCCESS");

        sessionAttendanceCommandRepository.save(cmd);

        AttendanceRowView currentSnapshot = new AttendanceRowView(
                studentPublicId,
                enrollment.getPublicId(),
                null,
                null,
                row.getStatus(),
                row.getVersion(),
                row.getLastActorPublicId(),
                row.getLastActorRole(),
                row.getLastActionAt(),
                row.getLastReasonOrNote(),
                true,
                null,
                null,
                null,
                row.getIdentityStatus(),
                row.getIdentityNote()
        );

        return toReceiptView(cmd, sessionPublicId, currentSnapshot);
    }

    @Transactional
    public AttendanceRowView recordIdentityVerification(
            UUID sessionPublicId,
            UUID studentPublicId,
            RecordIdentityVerificationRequest request,
            CurrentUser caller,
            boolean isHost) {

        UUID tenantId = caller.tenantId();

        ExamSession session = examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);

        Instant now = clock.instant();

        if (request.requestPublicId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestPublicId is required");
        }
        if (request.expectedVersion() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedVersion is required");
        }
        if (request.status() == null || request.status().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        String newStatus = request.status().trim().toUpperCase();
        if (!Set.of("UNVERIFIED", "VERIFIED", "FAILED").contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Identity status must be one of: UNVERIFIED, VERIFIED, FAILED");
        }

        if (!isHost) {
            boolean assigned = proctorAssignmentRepository.isProctorAssigned(sessionPublicId, caller.userId(), tenantId);
            if (!assigned) {
                throw new com.pte.session.internal.exception.ProctorNotAssignedException();
            }
        }

        // Idempotent receipt lookup before mutable state checks
        String fingerprint = computeIdentityFingerprint(studentPublicId, request.enrollmentPublicId(),
                request.expectedVersion(), newStatus, request.note());

        if (sessionIdentityVerificationCommandRepository != null) {
            Optional<SessionIdentityVerificationCommand> existingCmd = sessionIdentityVerificationCommandRepository
                    .findByTenantIdAndSessionIdAndActorPublicIdAndRequestPublicId(
                            tenantId, session.getId(), caller.userId(), request.requestPublicId());

            if (existingCmd.isPresent()) {
                SessionIdentityVerificationCommand saved = existingCmd.get();
                if (!saved.getIntentFingerprint().equals(fingerprint)) {
                    throw new AttendanceIdempotencyConflictException("Request ID " + request.requestPublicId()
                            + " already used with a different command intent");
                }
                Enrollment activeEnrollment = enrollmentRepository
                        .findActiveEnrollment(sessionPublicId, studentPublicId, tenantId)
                        .orElse(null);
                return buildCurrentSnapshot(session, activeEnrollment, studentPublicId, now, isHost);
            }
        }

        // Operational window and session status check
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new AttendanceWindowClosedException("Cannot record identity verification for a cancelled session");
        }

        if (isHost) {
            if (session.getStatus() != SessionStatus.SCHEDULED
                    && session.getStatus() != SessionStatus.OPEN
                    && session.getStatus() != SessionStatus.CLOSED) {
                throw new AttendanceWindowClosedException("Host identity verification is only permitted for SCHEDULED, OPEN, or CLOSED sessions");
            }
            if (session.getStatus() == SessionStatus.CLOSED) {
                if (request.note() == null || request.note().trim().isBlank()) {
                    throw new AttendanceReasonRequiredException("Reason is mandatory for Host identity verification on closed session");
                }
            }
        } else {
            if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.OPEN) {
                throw new AttendanceWindowClosedException("Proctor identity verification is only allowed for SCHEDULED or OPEN sessions");
            }
            Instant opensAt = session.getOpensAt();
            Instant closesAt = session.getClosesAt();
            if (opensAt == null || closesAt == null) {
                throw new AttendanceWindowClosedException("Session timing is missing");
            }
            Instant windowOpensAt = opensAt.minus(30, ChronoUnit.MINUTES);
            if (now.isBefore(windowOpensAt)) {
                throw new AttendanceWindowClosedException("Identity verification is not permitted before 30 minutes prior to session opening");
            }
            if (!now.isBefore(closesAt)) {
                throw new AttendanceWindowClosedException("Identity verification is closed after session closing time");
            }
        }

        Enrollment enrollment = enrollmentRepository
                .findActiveEnrollment(sessionPublicId, studentPublicId, tenantId)
                .orElseThrow(EnrollmentNotFoundException::new);

        if (!enrollment.getPublicId().equals(request.enrollmentPublicId())) {
            throw new AttendanceConflictException("Enrollment generation mismatch: request targeted "
                    + request.enrollmentPublicId() + " but active enrollment is " + enrollment.getPublicId());
        }

        Optional<SessionAttendance> optRow = sessionAttendanceRepository
                .findBySessionIdAndStudentPublicId(session.getId(), studentPublicId);

        Long currentEffectiveVersion = optRow.map(SessionAttendance::getVersion).orElse(0L);

        // Strict version conflict check (non-null enforced)
        if (!request.expectedVersion().equals(currentEffectiveVersion)) {
            throw new AttendanceConflictException("Identity verification version conflict: expected "
                    + request.expectedVersion() + " but current version is " + currentEffectiveVersion);
        }

        // Check if row belongs to currently active enrollment generation
        boolean isCurrentEnrollment = optRow.isPresent() && enrollment.getPublicId().equals(optRow.get().getEnrollmentPublicId());
        String currentEffectiveStatus = isCurrentEnrollment
                ? (optRow.get().getIdentityStatus() != null ? optRow.get().getIdentityStatus() : "UNVERIFIED")
                : "UNVERIFIED";

        String canonicalNote = request.note() != null && !request.note().trim().isBlank() ? request.note().trim() : null;
        String actorRole = isHost ? "HOST_ADMIN" : "PROCTOR";

        SessionAttendance row = optRow.orElseGet(() -> {
            SessionAttendance newRow = new SessionAttendance();
            newRow.setTenantId(tenantId);
            newRow.setSession(session);
            newRow.setStudentPublicId(studentPublicId);
            newRow.setEnrollmentPublicId(enrollment.getPublicId());
            newRow.setStatus(AttendanceStatus.UNMARKED);
            newRow.setVersion(0L);
            return newRow;
        });

        boolean enrollmentChanged = !enrollment.getPublicId().equals(row.getEnrollmentPublicId());
        boolean statusChanged = !newStatus.equals(currentEffectiveStatus);
        boolean noteChanged = !Objects.equals(canonicalNote, row.getIdentityNote());
        boolean changed = false;
        Long appliedVersion = currentEffectiveVersion;

        if (statusChanged || noteChanged || enrollmentChanged) {
            changed = true;
            appliedVersion = currentEffectiveVersion + 1;
            // Crucial fix: always bind row to current active enrollment generation
            row.setEnrollmentPublicId(enrollment.getPublicId());
            row.setIdentityStatus(newStatus);
            row.setIdentityNote(canonicalNote);
            row.setLastActorPublicId(caller.userId());
            row.setLastActorRole(actorRole);
            row.setLastActionAt(now);
            row.setVersion(appliedVersion);
            sessionAttendanceRepository.save(row);
        }

        if (sessionIdentityVerificationCommandRepository != null) {
            SessionIdentityVerificationCommand cmd = new SessionIdentityVerificationCommand();
            cmd.setTenantId(tenantId);
            cmd.setSession(session);
            cmd.setActorPublicId(caller.userId());
            cmd.setActorRole(actorRole);
            cmd.setRequestPublicId(request.requestPublicId());
            cmd.setIntentFingerprint(fingerprint);
            cmd.setStudentPublicId(studentPublicId);
            cmd.setEnrollmentPublicId(enrollment.getPublicId());
            cmd.setExpectedVersion(currentEffectiveVersion);
            cmd.setAppliedVersion(appliedVersion);
            cmd.setPreviousStatus(currentEffectiveStatus);
            cmd.setNewStatus(newStatus);
            cmd.setNote(canonicalNote);
            cmd.setServerActionAt(now);
            cmd.setChanged(changed);
            cmd.setCommittedResult(changed ? "SUCCESS" : "NO_OP");
            sessionIdentityVerificationCommandRepository.save(cmd);
        }

        return buildCurrentSnapshot(session, enrollment, studentPublicId, now, isHost);
    }

    @Transactional(readOnly = true)
    public AttendanceCommandReceiptView getReceipt(
            UUID sessionPublicId,
            UUID requestPublicId,
            CurrentUser caller,
            boolean isHost) {

        UUID tenantId = caller.tenantId();

        // R3: Validate current reader authorization before lookup or disclosing current snapshot
        if (!isHost) {
            boolean assigned = proctorAssignmentRepository.isProctorAssigned(sessionPublicId, caller.userId(), tenantId);
            if (!assigned) {
                throw new com.pte.session.internal.exception.ProctorNotAssignedException();
            }
        }

        SessionAttendanceCommand cmd = sessionAttendanceCommandRepository
                .findReceipt(tenantId, sessionPublicId, caller.userId(), requestPublicId)
                .orElseThrow(() -> new com.pte.session.internal.exception.AttendanceReceiptNotFoundException(
                        "Attendance command receipt not found for request " + requestPublicId));

        ExamSession session = examSessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);

        Enrollment enrollment = enrollmentRepository.findActiveEnrollment(sessionPublicId, cmd.getStudentPublicId(), tenantId)
                .orElse(null);

        AttendanceRowView currentSnapshot = buildCurrentSnapshot(session, enrollment, cmd.getStudentPublicId(), clock.instant(), isHost);
        return toReceiptView(cmd, sessionPublicId, currentSnapshot);
    }

    @Transactional(readOnly = true)
    public AttendanceHistoryView getHistory(
            UUID sessionPublicId,
            UUID studentPublicId,
            int requestedPage,
            int requestedSize,
            CurrentUser caller,
            boolean isHost) {

        UUID tenantId = caller.tenantId();

        if (!isHost) {
            boolean assigned = proctorAssignmentRepository.isProctorAssigned(sessionPublicId, caller.userId(), tenantId);
            if (!assigned) {
                throw new com.pte.session.internal.exception.ProctorNotAssignedException();
            }
        }

        int page = Math.max(requestedPage, 0);
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, 100);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "serverActionAt", "id"));
        Page<SessionAttendanceCommand> historyPage = sessionAttendanceCommandRepository
                .findHistory(tenantId, sessionPublicId, studentPublicId, pageRequest);

        var data = historyPage.getContent().stream()
                .map(c -> new AttendanceHistoryItemView(
                        c.getPublicId(),
                        c.getRequestPublicId(),
                        c.getStudentPublicId(),
                        c.getEnrollmentPublicId(),
                        c.getActorPublicId(),
                        null,
                        c.getActorRole(),
                        c.getAppliedVersion(),
                        c.getPreviousStatus(),
                        c.getNewStatus(),
                        c.getReasonOrNote(),
                        c.getServerActionAt()
                ))
                .toList();

        PageMeta meta = new PageMeta(
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages(),
                historyPage.isFirst(),
                historyPage.isLast(),
                historyPage.hasNext(),
                historyPage.hasPrevious()
        );

        return new AttendanceHistoryView(sessionPublicId, studentPublicId, data, meta, clock.instant());
    }

    private AttendanceRowView buildCurrentSnapshot(
            ExamSession session,
            Enrollment enrollment,
            UUID studentPublicId,
            Instant now,
            boolean isHost) {

        if (enrollment == null) {
            return new AttendanceRowView(
                    studentPublicId, null, null, null,
                    AttendanceStatus.UNMARKED, 0L, null, null, null, null, false, "NOT_ENROLLED", null, null, "UNVERIFIED", null);
        }

        Optional<SessionAttendance> optRow = sessionAttendanceRepository
                .findBySessionIdAndStudentPublicId(session.getId(), studentPublicId);

        Long monotonicVersion = optRow.map(SessionAttendance::getVersion).orElse(0L);

        if (optRow.isPresent()) {
            SessionAttendance row = optRow.get();
            if (enrollment.getPublicId().equals(row.getEnrollmentPublicId())) {
                return new AttendanceRowView(
                        studentPublicId,
                        enrollment.getPublicId(),
                        null,
                        null,
                        row.getStatus(),
                        row.getVersion(),
                        row.getLastActorPublicId(),
                        row.getLastActorRole(),
                        row.getLastActionAt(),
                        row.getLastReasonOrNote(),
                        true,
                        null,
                        null,
                        null,
                        row.getIdentityStatus(),
                        row.getIdentityNote()
                );
            }
        }

        // C1: Re-enrolled student displays UNMARKED while retaining existing monotonic version
        return new AttendanceRowView(
                studentPublicId,
                enrollment.getPublicId(),
                null,
                null,
                AttendanceStatus.UNMARKED,
                monotonicVersion,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                optRow.map(SessionAttendance::getIdentityStatus).orElse("UNVERIFIED"),
                optRow.map(SessionAttendance::getIdentityNote).orElse(null)
        );
    }

    private AttendanceCommandReceiptView toReceiptView(
            SessionAttendanceCommand cmd,
            UUID sessionPublicId,
            AttendanceRowView snapshot) {
        return new AttendanceCommandReceiptView(
                cmd.getRequestPublicId(),
                sessionPublicId,
                cmd.getStudentPublicId(),
                cmd.getEnrollmentPublicId(),
                cmd.getActorPublicId(),
                cmd.getActorRole(),
                cmd.getAppliedVersion(),
                cmd.getPreviousStatus(),
                cmd.getNewStatus(),
                cmd.getReasonOrNote(),
                cmd.getServerActionAt(),
                Boolean.TRUE.equals(cmd.getChanged()),
                cmd.getCommittedResult(),
                snapshot
        );
    }

    private String computeFingerprint(UUID studentId, UUID enrollmentId, Long version, AttendanceStatus status, String note) {
        String canonicalNote = note != null ? note.trim() : "";
        String raw = studentId + ":" + enrollmentId + ":" + version + ":" + status + ":" + canonicalNote;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    private String computeIdentityFingerprint(UUID studentId, UUID enrollmentId, Long version, String status, String note) {
        String canonicalNote = note != null ? note.trim() : "";
        String canonicalStatus = status != null ? status.trim().toUpperCase() : "";
        long canonicalVersion = version != null ? version : 0L;
        String raw = studentId + ":" + enrollmentId + ":" + canonicalVersion + ":" + canonicalStatus + ":" + canonicalNote;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}
