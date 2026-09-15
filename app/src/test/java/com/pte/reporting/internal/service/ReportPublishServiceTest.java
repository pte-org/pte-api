package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import com.pte.reporting.internal.repository.AttemptReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPublishServiceTest {

    @Mock
    private AttemptService attemptService;

    @Mock
    private AttemptReportRepository attemptReportRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReportPublishService service;

    @BeforeEach
    void setUp() {
        service = new ReportPublishService(attemptService, attemptReportRepository, eventPublisher);
    }

    @Test
    void publishSession_publishesUnpublishedAttempts() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(false);

        when(attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(attemptSummary));
        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));
        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.publishSession(sessionPublicId, tenantId);

        assertThat(report.isPublished()).isTrue();
        verify(attemptReportRepository).save(report);
        verify(eventPublisher).publishEvent(any(AttemptPublishedEvent.class));
    }

    @Test
    void publishSession_skipsAlreadyPublishedAttempts() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(true);

        when(attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(attemptSummary));
        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));

        service.publishSession(sessionPublicId, tenantId);

        verify(attemptReportRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishSession_createsReportIfNotExists() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);

        when(attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(attemptSummary));
        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.empty());
        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenAnswer(inv -> {
                    AttemptReport report = inv.getArgument(0);
                    report.setId(1L);
                    return report;
                });

        service.publishSession(sessionPublicId, tenantId);

        verify(attemptReportRepository, times(2)).save(any(AttemptReport.class));
        verify(eventPublisher).publishEvent(any(AttemptPublishedEvent.class));
    }

    @Test
    void publishSession_emitsEventWithCorrectFields() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(false);

        when(attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(attemptSummary));
        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));
        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.publishSession(sessionPublicId, tenantId);

        ArgumentCaptor<AttemptPublishedEvent> captor = ArgumentCaptor.forClass(AttemptPublishedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        AttemptPublishedEvent event = captor.getValue();
        assertThat(event.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(event.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(event.studentPublicId()).isEqualTo(studentPublicId);
        assertThat(event.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void publishSession_mixedAttempts_publishesOnlyUnpublished() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        UUID attemptId1 = UUID.randomUUID();
        UUID studentId1 = UUID.randomUUID();
        AttemptSummaryView attempt1 = new AttemptSummaryView(attemptId1, sessionPublicId, studentId1, tenantId);

        UUID attemptId2 = UUID.randomUUID();
        UUID studentId2 = UUID.randomUUID();
        AttemptSummaryView attempt2 = new AttemptSummaryView(attemptId2, sessionPublicId, studentId2, tenantId);

        UUID attemptId3 = UUID.randomUUID();
        UUID studentId3 = UUID.randomUUID();
        AttemptSummaryView attempt3 = new AttemptSummaryView(attemptId3, sessionPublicId, studentId3, tenantId);

        // Attempt 1: doesn't exist, will be created and published
        // Attempt 2: exists but unpublished, will be published
        // Attempt 3: exists and already published, will be skipped

        AttemptReport report2 = new AttemptReport();
        report2.setAttemptPublicId(attemptId2);
        report2.setSessionPublicId(sessionPublicId);
        report2.setStudentPublicId(studentId2);
        report2.setTenantId(tenantId);
        report2.setPublished(false);

        AttemptReport report3 = new AttemptReport();
        report3.setAttemptPublicId(attemptId3);
        report3.setSessionPublicId(sessionPublicId);
        report3.setStudentPublicId(studentId3);
        report3.setTenantId(tenantId);
        report3.setPublished(true);

        when(attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(attempt1, attempt2, attempt3));

        when(attemptReportRepository.findByAttemptPublicId(attemptId1))
                .thenReturn(Optional.empty());
        when(attemptReportRepository.findByAttemptPublicId(attemptId2))
                .thenReturn(Optional.of(report2));
        when(attemptReportRepository.findByAttemptPublicId(attemptId3))
                .thenReturn(Optional.of(report3));

        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenAnswer(inv -> {
                    AttemptReport report = inv.getArgument(0);
                    report.setId(1L);
                    return report;
                });

        service.publishSession(sessionPublicId, tenantId);

        // Should publish exactly twice: once for new attempt1, once for existing unpublished attempt2
        verify(eventPublisher, times(2)).publishEvent(any(AttemptPublishedEvent.class));
    }

    @Test
    void publishSession_concurrentCreateRace_recoversAndPublishes() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        AttemptReport savedReport = new AttemptReport();
        savedReport.setAttemptPublicId(attemptPublicId);
        savedReport.setSessionPublicId(sessionPublicId);
        savedReport.setStudentPublicId(studentPublicId);
        savedReport.setTenantId(tenantId);
        savedReport.setPublished(false);

        when(attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(attemptSummary));
        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedReport));
        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"))
                .thenAnswer(inv -> inv.getArgument(0));

        service.publishSession(sessionPublicId, tenantId);

        verify(eventPublisher).publishEvent(any(AttemptPublishedEvent.class));
    }
}
