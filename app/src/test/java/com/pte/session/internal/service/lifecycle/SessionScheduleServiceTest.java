package com.pte.session.internal.service.lifecycle;

import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.PatchExamScheduleRequest;
import com.pte.session.internal.exception.ExamCloseTimeNotExtendedException;
import com.pte.session.internal.exception.ExamScheduleNotEditableException;
import com.pte.session.internal.exception.ExamScheduleVersionConflictException;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionScheduleServiceTest {

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private SessionSubscriptionRules subscriptions;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SubscriptionView subscription;

    private SessionScheduleService service;
    private ExamSession session;
    private CurrentUser caller;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-13T12:00:00Z");
        SessionAccess access = new SessionAccess(sessionRepository);
        service = new SessionScheduleService(sessionRepository, access, subscriptions, auditLogService,
                Clock.fixed(now, ZoneOffset.UTC));
        session = session(SessionStatus.SCHEDULED, now.plusSeconds(3600), now.plusSeconds(7200));
        caller = new CurrentUser(UUID.randomUUID(), session.getTenantId(), List.of("HOST_ADMIN"));
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), session.getTenantId()))
                .thenReturn(Optional.of(session));
    }

    @Test
    void scheduledSession_updatesNameAndWindow() {
        Instant newOpensAt = now.plusSeconds(1800);
        Instant newClosesAt = now.plusSeconds(5400);
        when(subscriptions.lockedActiveSubscription(session.getSubscriptionId(), session.getTenantId()))
                .thenReturn(subscription);
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        var response = service.patchSchedule(session.getPublicId(),
                new PatchExamScheduleRequest("Updated exam", newOpensAt, newClosesAt, 3L), caller);

        assertThat(response.name()).isEqualTo("Updated exam");
        assertThat(session.getOpensAt()).isEqualTo(newOpensAt);
        assertThat(session.getClosesAt()).isEqualTo(newClosesAt);
        verify(auditLogService).record(eq(caller), eq(SessionConstants.AGGREGATE_EXAM_SESSION),
                eq(session.getPublicId().toString()), eq(SessionConstants.EVENT_EXAM_SCHEDULE_UPDATED),
                eq("Updated exam"));
    }

    @Test
    void openSession_rejectsShorteningOrKeepingTheCloseTime() {
        session.setStatus(SessionStatus.OPEN);

        assertThatThrownBy(() -> service.patchSchedule(session.getPublicId(),
                new PatchExamScheduleRequest(null, null, session.getClosesAt(), 3L), caller))
                .isInstanceOf(ExamCloseTimeNotExtendedException.class);
    }

    @Test
    void staleVersion_isRejectedBeforeMutation() {
        assertThatThrownBy(() -> service.patchSchedule(session.getPublicId(),
                new PatchExamScheduleRequest("Updated", null, null, 2L), caller))
                .isInstanceOf(ExamScheduleVersionConflictException.class);
    }

    @Test
    void scheduledFormSavedAfterOpeningTime_appliesOpenRules() {
        session.setOpensAt(now.minusSeconds(60));
        Instant extendedClose = session.getClosesAt().plusSeconds(600);
        when(subscriptions.lockedActiveSubscription(session.getSubscriptionId(), session.getTenantId()))
                .thenReturn(subscription);
        when(sessionRepository.saveAndFlush(session)).thenReturn(session);

        var response = service.patchSchedule(session.getPublicId(),
                new PatchExamScheduleRequest(session.getName(), session.getOpensAt(), extendedClose, 3L), caller);

        assertThat(response.status()).isEqualTo(SessionStatus.OPEN.name());
        assertThat(session.getClosesAt()).isEqualTo(extendedClose);
        verify(auditLogService).record(any(CurrentUser.class), eq(SessionConstants.AGGREGATE_EXAM_SESSION),
                eq(session.getPublicId().toString()), eq(SessionConstants.EVENT_EXAM_AUTO_OPENED), any());
    }

    @Test
    void scheduledFormSavedAfterOpeningTime_rejectsIdentityChanges() {
        session.setOpensAt(now.minusSeconds(60));

        assertThatThrownBy(() -> service.patchSchedule(session.getPublicId(),
                new PatchExamScheduleRequest("Changed", session.getOpensAt(), session.getClosesAt(), 3L), caller))
                .isInstanceOf(ExamScheduleNotEditableException.class);
    }

    private ExamSession session(SessionStatus status, Instant opensAt, Instant closesAt) {
        ExamSession value = new ExamSession();
        value.setPublicId(UUID.randomUUID());
        value.setTenantId(UUID.randomUUID());
        value.setSubscriptionId(UUID.randomUUID());
        value.setName("Original exam");
        value.setStatus(status);
        value.setOpensAt(opensAt);
        value.setClosesAt(closesAt);
        value.setCapacity(10);
        value.setDraftVersion(3L);
        value.setPolicy(ExamPolicy.realExamDefault());
        return value;
    }
}
