package com.pte.session.internal.repository;

import com.pte.session.SessionOverviewService;
import com.pte.session.UpcomingSessionView;
import com.pte.session.domain.ExamRegistration;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Session counts and the upcoming list behind {@link SessionOverviewService} on the embedded database. */
@DataJpaTest
@Import(SessionOverviewService.class)
class SessionOverviewQueriesTest {

    private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private SessionOverviewService service;

    @Autowired
    private ExamSessionRepository sessions;

    @Autowired
    private ExamRegistrationRepository enrollments;

    @Autowired
    private EntityManager em;

    private ExamSession session(UUID tenant, SessionStatus status, Instant opensAt, Instant closesAt,
                                boolean deleted, Instant createdAt) {
        ExamSession s = new ExamSession();
        s.setName("Session " + UUID.randomUUID());
        s.setSessionCode("S-" + UUID.randomUUID().toString().substring(0, 20));
        s.setTenantId(tenant);
        s.setSubscriptionId(UUID.randomUUID());
        s.setLicenseKey("LIC");
        s.setOpensAt(opensAt);
        s.setClosesAt(closesAt);
        s.setCapacity(30);
        s.setStatus(status);
        s.setDeleted(deleted);
        // The entity carries a non-null @Version, so save() merges: use the returned managed copy.
        ExamSession saved = sessions.saveAndFlush(s);
        if (createdAt != null) {
            em.createQuery("update ExamSession s set s.createdAt = :createdAt where s.id = :id")
                    .setParameter("createdAt", createdAt).setParameter("id", saved.getId()).executeUpdate();
        }
        em.clear();
        return saved;
    }

    private ExamSession future(UUID tenant, SessionStatus status) {
        return session(tenant, status, NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(1)).plusSeconds(7200),
                false, null);
    }

    private void enroll(ExamSession session, UUID tenant, boolean deleted) {
        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setSession(sessions.getReferenceById(session.getId()));
        enrollment.setStudentPublicId(UUID.randomUUID());
        enrollment.setTenantId(tenant);
        enrollment.setLicenseKey("LIC");
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        enrollment.setDeleted(deleted);
        enrollments.saveAndFlush(enrollment);
        em.clear();
    }

    // ---- countByStatus ----

    @Test
    void countByStatusListsEveryStatusInEnumOrderWithZeroFill() {
        UUID tenant = UUID.randomUUID();
        future(tenant, SessionStatus.DRAFT);
        future(tenant, SessionStatus.SCHEDULED);
        future(tenant, SessionStatus.SCHEDULED);
        future(tenant, SessionStatus.OPEN);

        Map<String, Long> byStatus = service.countByStatus(tenant);

        assertThat(byStatus.keySet()).containsExactly(
                Arrays.stream(SessionStatus.values()).map(Enum::name).toArray(String[]::new));
        assertThat(byStatus).containsEntry("DRAFT", 1L).containsEntry("SCHEDULED", 2L).containsEntry("OPEN", 1L)
                .containsEntry("READY", 0L).containsEntry("CLOSED", 0L)
                .containsEntry("CANCELLED", 0L);
    }

    @Test
    void countByStatusIgnoresDeletedSessionsAndOtherTenants() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        future(tenant, SessionStatus.OPEN);
        session(tenant, SessionStatus.OPEN, NOW, NOW.plusSeconds(60), true, null); // deleted
        future(other, SessionStatus.OPEN);
        future(other, SessionStatus.OPEN);

        assertThat(service.countByStatus(tenant)).containsEntry("OPEN", 1L);
        assertThat(service.countByStatus(other)).containsEntry("OPEN", 2L);
    }

    @Test
    void countByStatusOfAnEmptyTenantIsAllZeros() {
        future(UUID.randomUUID(), SessionStatus.OPEN);

        Map<String, Long> byStatus = service.countByStatus(UUID.randomUUID());

        assertThat(byStatus).hasSize(SessionStatus.values().length);
        assertThat(byStatus.values()).containsOnly(0L);
    }

    // ---- countCreatedBetween ----

    @Test
    void createdBetweenIsHalfOpenAndIgnoresDeletedAndOtherTenants() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        session(tenant, SessionStatus.DRAFT, NOW, NOW.plusSeconds(60), false, FROM); // included
        session(tenant, SessionStatus.OPEN, NOW, NOW.plusSeconds(60), false, TO.minusSeconds(1));
        session(tenant, SessionStatus.OPEN, NOW, NOW.plusSeconds(60), false, TO); // excluded
        session(tenant, SessionStatus.OPEN, NOW, NOW.plusSeconds(60), false, FROM.minusSeconds(1)); // excluded
        session(tenant, SessionStatus.OPEN, NOW, NOW.plusSeconds(60), true, Instant.parse("2026-09-10T00:00:00Z"));
        session(other, SessionStatus.OPEN, NOW, NOW.plusSeconds(60), false, Instant.parse("2026-09-10T00:00:00Z"));

        assertThat(service.countCreatedBetween(tenant, FROM, TO)).isEqualTo(2);
        assertThat(service.countCreatedBetween(other, FROM, TO)).isEqualTo(1);
    }

    @Test
    void createdBetweenCountsSessionsOfEveryStatus() {
        UUID tenant = UUID.randomUUID();
        Instant created = Instant.parse("2026-09-10T00:00:00Z");
        for (SessionStatus status : SessionStatus.values()) {
            session(tenant, status, NOW, NOW.plusSeconds(60), false, created);
        }

        assertThat(service.countCreatedBetween(tenant, FROM, TO)).isEqualTo(SessionStatus.values().length);
    }

    // ---- upcoming ----

    @Test
    void upcomingReturnsOnlyJoinableUnclosedSessionsSoonestOpeningFirstWithEnrolledCounts() {
        UUID tenant = UUID.randomUUID();
        ExamSession openSoon = session(tenant, SessionStatus.OPEN, NOW.minus(Duration.ofHours(1)),
                NOW.plus(Duration.ofHours(1)), false, null);
        ExamSession openEarlier = session(tenant, SessionStatus.OPEN, NOW.minus(Duration.ofHours(3)),
                NOW.plus(Duration.ofMinutes(30)), false, null);
        ExamSession tomorrow = session(tenant, SessionStatus.SCHEDULED, NOW.plus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(1)).plusSeconds(7200), false, null);
        ExamSession dayAfter = session(tenant, SessionStatus.SCHEDULED, NOW.plus(Duration.ofDays(2)),
                NOW.plus(Duration.ofDays(2)).plusSeconds(7200), false, null);
        // Excluded: wrong status, already closed (closesAt == now is closed), deleted, other tenant.
        session(tenant, SessionStatus.CLOSED, NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofDays(2)), false, null);
        session(tenant, SessionStatus.DRAFT, NOW.plusSeconds(3600), NOW.plusSeconds(10800), false, null);
        session(tenant, SessionStatus.READY, NOW.plusSeconds(3600), NOW.plusSeconds(10800), false, null);
        session(tenant, SessionStatus.CANCELLED, NOW.plusSeconds(3600), NOW.plusSeconds(10800), false, null);
        session(tenant, SessionStatus.SCHEDULED, NOW.minus(Duration.ofHours(5)), NOW, false, null);
        session(tenant, SessionStatus.SCHEDULED, NOW.plusSeconds(3600), NOW.plusSeconds(10800), true, null);
        future(UUID.randomUUID(), SessionStatus.SCHEDULED);

        enroll(openSoon, tenant, false);
        enroll(openSoon, tenant, false);
        enroll(openSoon, tenant, false);
        enroll(openSoon, tenant, true); // removed enrollment is not counted
        enroll(dayAfter, tenant, false);

        List<UpcomingSessionView> upcoming = service.upcoming(tenant, NOW, 10);

        assertThat(upcoming).extracting(UpcomingSessionView::publicId).containsExactly(
                openEarlier.getPublicId(), openSoon.getPublicId(), tomorrow.getPublicId(), dayAfter.getPublicId());
        assertThat(upcoming).extracting(UpcomingSessionView::enrolled).containsExactly(0L, 3L, 0L, 1L);
        UpcomingSessionView first = upcoming.get(1);
        assertThat(first.name()).isEqualTo(openSoon.getName());
        assertThat(first.status()).isEqualTo("OPEN");
        assertThat(first.opensAt()).isEqualTo(NOW.minus(Duration.ofHours(1)));
        assertThat(first.closesAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(first.capacity()).isEqualTo(30);
    }

    @Test
    void upcomingHonoursTheLimitAndTreatsNonPositiveLimitsAsEmpty() {
        UUID tenant = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            session(tenant, SessionStatus.SCHEDULED, NOW.plus(Duration.ofDays(i + 1)),
                    NOW.plus(Duration.ofDays(i + 1)).plusSeconds(3600), false, null);
        }

        assertThat(service.upcoming(tenant, NOW, 2)).hasSize(2);
        assertThat(service.upcoming(tenant, NOW, 0)).isEmpty();
        assertThat(service.upcoming(tenant, NOW, -3)).isEmpty();
    }

    @Test
    void upcomingNeverLeaksAnotherTenantsSessionsOrEnrollmentCounts() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ExamSession mine = future(tenant, SessionStatus.SCHEDULED);
        ExamSession theirs = future(other, SessionStatus.SCHEDULED);
        enroll(theirs, other, false);
        enroll(theirs, other, false);

        List<UpcomingSessionView> upcoming = service.upcoming(tenant, NOW, 10);

        assertThat(upcoming).extracting(UpcomingSessionView::publicId).containsExactly(mine.getPublicId());
        assertThat(upcoming.get(0).enrolled()).isZero();
    }

    @Test
    void upcomingOfATenantWithNothingScheduledIsEmpty() {
        future(UUID.randomUUID(), SessionStatus.SCHEDULED);

        assertThat(service.upcoming(UUID.randomUUID(), NOW, 5)).isEmpty();
    }
}
