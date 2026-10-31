package com.pte.session.internal.repository;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExamRegistrationRepositorySlotConflictTest {

    @Autowired
    private ExamRegistrationRepository registrationRepository;

    @Autowired
    private ExamSessionRepository sessionRepository;

    @Test
    void findConflictingEnrollments_usesHalfOpenWindowAndIgnoresCancelledSessions() {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ExamSession existing = saveSession(tenantId, "Existing", SessionStatus.SCHEDULED,
                "2026-10-17T08:00:00Z", "2026-10-17T10:00:00Z");
        enroll(existing, tenantId, studentId);

        assertThat(registrationRepository.findConflictingEnrollments(tenantId, List.of(studentId),
                Instant.parse("2026-10-17T10:00:00Z"), Instant.parse("2026-10-17T12:00:00Z")))
                .isEmpty();

        List<ExamRegistrationRepository.SlotConflictProjection> overlaps =
                registrationRepository.findConflictingEnrollments(tenantId, List.of(studentId),
                        Instant.parse("2026-10-17T09:00:00Z"), Instant.parse("2026-10-17T11:00:00Z"));
        assertThat(overlaps).hasSize(1);
        assertThat(overlaps.get(0).getConflictingSessionName()).isEqualTo("Existing");

        existing.setStatus(SessionStatus.CANCELLED);
        sessionRepository.saveAndFlush(existing);

        assertThat(registrationRepository.findConflictingEnrollments(tenantId, List.of(studentId),
                Instant.parse("2026-10-17T09:00:00Z"), Instant.parse("2026-10-17T11:00:00Z")))
                .isEmpty();
    }

    @Test
    void findConflictingEnrollments_isTenantScoped() {
        UUID ownerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ExamSession existing = saveSession(ownerTenantId, "Other tenant exam", SessionStatus.OPEN,
                "2026-10-17T08:00:00Z", "2026-10-17T10:00:00Z");
        enroll(existing, ownerTenantId, studentId);

        assertThat(registrationRepository.findConflictingEnrollments(otherTenantId, List.of(studentId),
                Instant.parse("2026-10-17T09:00:00Z"), Instant.parse("2026-10-17T11:00:00Z")))
                .isEmpty();
    }

    @ParameterizedTest
    @EnumSource(SessionStatus.class)
    void onlyScheduledAndOpenSessionsBlockAStudent(SessionStatus status) {
        UUID tenantId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        enroll(saveSession(tenantId, "Earlier exam", status,
                "2026-10-17T08:00:00Z", "2026-10-17T10:00:00Z"), tenantId, studentId);
        var conflicts = registrationRepository.findConflictingEnrollments(tenantId, List.of(studentId),
                Instant.parse("2026-10-17T09:00:00Z"), Instant.parse("2026-10-17T11:00:00Z"));
        assertThat(conflicts).hasSize(status == SessionStatus.SCHEDULED || status == SessionStatus.OPEN ? 1 : 0);
    }

    private ExamSession saveSession(UUID tenantId, String name, SessionStatus status,
            String opensAt, String closesAt) {
        ExamSession session = new ExamSession();
        session.setName(name);
        session.setSessionCode("TST-" + UUID.randomUUID().toString().substring(0, 20));
        session.setTenantId(tenantId);
        session.setSubscriptionId(UUID.randomUUID());
        session.setLicenseKey("LIC-" + UUID.randomUUID());
        session.setOpensAt(Instant.parse(opensAt));
        session.setClosesAt(Instant.parse(closesAt));
        session.setStatus(status);
        session.setCapacity(10);
        return sessionRepository.saveAndFlush(session);
    }

    private void enroll(ExamSession session, UUID tenantId, UUID studentId) {
        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setSession(session);
        enrollment.setStudentPublicId(studentId);
        enrollment.setTenantId(tenantId);
        enrollment.setLicenseKey(session.getLicenseKey());
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        registrationRepository.saveAndFlush(enrollment);
    }
}
