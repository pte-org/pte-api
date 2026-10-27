package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionGradingStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.SessionGradingView;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grading and publication state stored on the session: freeze once, complete once, publish once. */
@DataJpaTest
@Import(SessionGradingService.class)
class SessionGradingServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-25T08:00:00Z");

    @Autowired
    private SessionGradingService service;

    @Autowired
    private ExamSessionRepository sessionRepository;

    @Test
    void aSessionWithoutFrozenGradingHasNoGradingView() {
        ExamSession session = closedSession(UUID.randomUUID());

        assertThat(service.findGrading(session.getPublicId(), session.getTenantId())).isEmpty();
        assertThat(service.findPublication(session.getPublicId(), session.getTenantId())).isEmpty();
    }

    @Test
    void freezingStoresTheModeVersionAndActorAndASecondFreezeIsRejected() {
        ExamSession session = closedSession(UUID.randomUUID());
        UUID actor = UUID.randomUUID();

        SessionGradingView frozen = service.freezeGrading(session.getPublicId(), session.getTenantId(),
                "MANUAL_EXAMINER", "preview-v1", actor, T0);

        assertThat(frozen.markingMode()).isEqualTo("MANUAL_EXAMINER");
        assertThat(frozen.status()).isEqualTo(SessionGradingStatus.FROZEN.name());
        assertThat(frozen.previewVersion()).isEqualTo("preview-v1");
        assertThat(frozen.frozenByPublicId()).isEqualTo(actor);
        assertThat(frozen.frozenAt()).isEqualTo(T0);
        assertThat(frozen.completedAt()).isNull();
        assertThatThrownBy(() -> service.freezeGrading(session.getPublicId(), session.getTenantId(),
                "AI_ONLY", "preview-v2", actor, T0.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SessionConstants.GRADING_ALREADY_FROZEN);
    }

    @Test
    void completionIsReportedOnlyByTheCallThatCompletesTheGrading() {
        ExamSession session = closedSession(UUID.randomUUID());
        UUID tenantId = session.getTenantId();
        UUID sessionId = session.getPublicId();

        assertThat(service.completeGrading(sessionId, tenantId, T0)).isFalse();

        service.freezeGrading(sessionId, tenantId, "AI_ONLY", "v", UUID.randomUUID(), T0);
        assertThat(service.completeGrading(sessionId, tenantId, T0.plusSeconds(60))).isTrue();
        assertThat(service.completeGrading(sessionId, tenantId, T0.plusSeconds(120))).isFalse();

        SessionGradingView completed = service.findGrading(sessionId, tenantId).orElseThrow();
        assertThat(completed.status()).isEqualTo(SessionGradingStatus.COMPLETED.name());
        assertThat(completed.completedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void onlyFrozenSessionsAreCountedAndListedForTheReconciliationSweep() {
        UUID tenantId = UUID.randomUUID();
        ExamSession frozen = closedSession(tenantId);
        ExamSession completed = closedSession(tenantId);
        closedSession(tenantId); // never frozen
        ExamSession otherTenant = closedSession(UUID.randomUUID());
        service.freezeGrading(frozen.getPublicId(), tenantId, "MANUAL_EXAMINER", "v", UUID.randomUUID(), T0);
        service.freezeGrading(completed.getPublicId(), tenantId, "AI_ONLY", "v", UUID.randomUUID(), T0);
        service.completeGrading(completed.getPublicId(), tenantId, T0);
        service.freezeGrading(otherTenant.getPublicId(), otherTenant.getTenantId(), "AI_ONLY", "v",
                UUID.randomUUID(), T0);

        assertThat(service.countFrozen(tenantId)).isEqualTo(1);
        assertThat(service.countFrozen(otherTenant.getTenantId())).isEqualTo(1);
        assertThat(service.countFrozen(UUID.randomUUID())).isZero();
        assertThat(service.findFrozen()).extracting(SessionGradingView::sessionPublicId)
                .contains(frozen.getPublicId(), otherTenant.getPublicId())
                .doesNotContain(completed.getPublicId());
    }

    @Test
    void publicationLocksTheSessionOnceAndRepeatingTheSamePublicationIsANoOp() {
        ExamSession session = closedSession(UUID.randomUUID());
        UUID tenantId = session.getTenantId();
        UUID sessionId = session.getPublicId();
        UUID publicationId = UUID.randomUUID();

        var locked = service.lockPublication(sessionId, tenantId, publicationId, T0);
        var repeated = service.lockPublication(sessionId, tenantId, publicationId, T0.plusSeconds(30));

        assertThat(locked.publicationPublicId()).isEqualTo(publicationId);
        assertThat(repeated.lockedAt()).isEqualTo(T0);
        assertThatThrownBy(() -> service.lockPublication(sessionId, tenantId, UUID.randomUUID(), T0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SessionConstants.PUBLICATION_ALREADY_LOCKED);
        assertThat(service.findPublication(sessionId, tenantId)).isPresent();
    }

    @Test
    void publicationsAreFoundBySessionAndNeverAcrossTenants() {
        ExamSession published = closedSession(UUID.randomUUID());
        ExamSession unpublished = closedSession(published.getTenantId());
        service.lockPublication(published.getPublicId(), published.getTenantId(), UUID.randomUUID(), T0);

        var found = service.findPublications(published.getTenantId(),
                List.of(published.getPublicId(), unpublished.getPublicId()));

        assertThat(found).extracting(view -> view.sessionPublicId()).containsExactly(published.getPublicId());
        assertThat(service.findPublications(UUID.randomUUID(), List.of(published.getPublicId()))).isEmpty();
        assertThat(service.findPublications(published.getTenantId(), List.of())).isEmpty();
    }

    private ExamSession closedSession(UUID tenantId) {
        ExamSession session = new ExamSession();
        session.setName("Grading session");
        session.setSessionCode("TST-" + UUID.randomUUID().toString().substring(0, 20));
        session.setTenantId(tenantId);
        session.setSubscriptionId(UUID.randomUUID());
        session.setLicenseKey("LIC-" + UUID.randomUUID());
        session.setOpensAt(T0.minusSeconds(7200));
        session.setClosesAt(T0.minusSeconds(3600));
        session.setStatus(SessionStatus.CLOSED);
        session.setCapacity(10);
        return sessionRepository.saveAndFlush(session);
    }
}
