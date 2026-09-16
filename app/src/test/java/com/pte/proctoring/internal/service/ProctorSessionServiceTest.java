package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.enums.ProctorSessionStatus;
import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.proctoring.internal.exception.ProctorSessionNotFoundException;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.repository.ProctorSessionRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorSessionServiceTest {

    @Mock
    private ProctorSessionRepository proctorSessionRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private ProctorMapper proctorMapper;

    private ProctorSessionService service;

    @BeforeEach
    void setUp() {
        service = new ProctorSessionService(proctorSessionRepository, sessionService, proctorMapper);
    }

    @Test
    void open_activeSessionExists_returnsExistingSessionWithoutCheckingAssignment() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ProctorSession existingSession = new ProctorSession();
        existingSession.setPublicId(UUID.randomUUID());
        existingSession.setSessionPublicId(sessionPublicId);
        existingSession.setProctorPublicId(proctorPublicId);
        existingSession.setTenantId(tenantId);
        existingSession.setStatus(ProctorSessionStatus.ACTIVE);

        ProctorSessionResponse expectedResponse = new ProctorSessionResponse(
                existingSession.getPublicId(), sessionPublicId, proctorPublicId, ProctorSessionStatus.ACTIVE,
                existingSession.getOpenedAt());

        when(proctorSessionRepository.findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                sessionPublicId, proctorPublicId, tenantId, ProctorSessionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSession));
        when(proctorMapper.toResponse(existingSession)).thenReturn(expectedResponse);

        ProctorSessionResponse result = service.open(sessionPublicId, caller);

        assertThat(result).isEqualTo(expectedResponse);
        verify(sessionService, never()).checkProctorAssignment(any(), any());
        verify(proctorSessionRepository, never()).save(any());
    }

    @Test
    void open_noActiveSession_createsNewSessionAfterCheckingAssignment() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        ProctorAssignmentCheckResponse checkResponse = new ProctorAssignmentCheckResponse(sessionPublicId, tenantId);
        when(proctorSessionRepository.findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                sessionPublicId, proctorPublicId, tenantId, ProctorSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId))
                .thenReturn(checkResponse);

        ProctorSession savedSession = new ProctorSession();
        savedSession.setPublicId(UUID.randomUUID());
        savedSession.setSessionPublicId(sessionPublicId);
        savedSession.setProctorPublicId(proctorPublicId);
        savedSession.setTenantId(tenantId);
        savedSession.setStatus(ProctorSessionStatus.ACTIVE);

        ProctorSessionResponse expectedResponse = new ProctorSessionResponse(
                savedSession.getPublicId(), sessionPublicId, proctorPublicId, ProctorSessionStatus.ACTIVE,
                savedSession.getOpenedAt());

        when(proctorSessionRepository.save(any(ProctorSession.class))).thenReturn(savedSession);
        when(proctorMapper.toResponse(savedSession)).thenReturn(expectedResponse);

        ProctorSessionResponse result = service.open(sessionPublicId, caller);

        assertThat(result).isEqualTo(expectedResponse);
        verify(sessionService).checkProctorAssignment(sessionPublicId, proctorPublicId);

        ArgumentCaptor<ProctorSession> captor = ArgumentCaptor.forClass(ProctorSession.class);
        verify(proctorSessionRepository).save(captor.capture());
        ProctorSession saved = captor.getValue();
        assertThat(saved.getSessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(saved.getProctorPublicId()).isEqualTo(proctorPublicId);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void open_checkProctorAssignmentThrows_propagatesException() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR"));

        when(proctorSessionRepository.findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                sessionPublicId, proctorPublicId, tenantId, ProctorSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId))
                .thenThrow(new RuntimeException("Proctor not assigned"));

        assertThatThrownBy(() -> service.open(sessionPublicId, caller))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Proctor not assigned");

        verify(proctorSessionRepository, never()).save(any());
    }

    @Test
    void close_activeSession_endsAndSaves() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ProctorSession session = new ProctorSession();
        session.setPublicId(sessionPublicId);
        session.setSessionPublicId(UUID.randomUUID());
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        session.setStatus(ProctorSessionStatus.ACTIVE);

        when(proctorSessionRepository.findByPublicIdAndProctorPublicIdAndTenantId(sessionPublicId, proctorPublicId, tenantId))
                .thenReturn(Optional.of(session));

        service.close(sessionPublicId, new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR")));

        assertThat(session.getStatus()).isEqualTo(ProctorSessionStatus.ENDED);
        assertThat(session.getClosedAt()).isNotNull();
        verify(proctorSessionRepository).save(session);
    }

    @Test
    void close_alreadyEndedSession_noOp() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ProctorSession session = new ProctorSession();
        session.setPublicId(sessionPublicId);
        session.setSessionPublicId(UUID.randomUUID());
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        session.setStatus(ProctorSessionStatus.ENDED);

        when(proctorSessionRepository.findByPublicIdAndProctorPublicIdAndTenantId(sessionPublicId, proctorPublicId, tenantId))
                .thenReturn(Optional.of(session));

        service.close(sessionPublicId, new CurrentUser(proctorPublicId, tenantId, List.of("PROCTOR")));

        verify(proctorSessionRepository, never()).save(any());
    }

    @Test
    void findOwned_sessionNotFound_throwsException() {
        UUID publicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(proctorSessionRepository.findByPublicIdAndProctorPublicIdAndTenantId(publicId, proctorPublicId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwned(publicId, proctorPublicId, tenantId))
                .isInstanceOf(ProctorSessionNotFoundException.class);
    }
}
