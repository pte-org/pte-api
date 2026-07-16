package com.aptis.modules.proctor.service;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.iam.repository.ProctorRepository;
import com.aptis.modules.proctor.dto.ProctorStatusMessage;

/**
 * Pushes a {@link ProctorStatusMessage} to the proctor assigned to an exam, whenever an
 * attempt's status changes (Phase 6 status model). A no-op if the exam has no assigned
 * proctor. Deliberately takes plain values, not an examdelivery.domain.ExamAttempt entity —
 * this keeps the dependency one-directional (examdelivery → proctor), not a cycle.
 */
@Service
public class ProctorStatusPushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProctorStatusPushService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ExamRepository examRepository;
    private final ProctorRepository proctorRepository;

    public ProctorStatusPushService(
            SimpMessagingTemplate messagingTemplate,
            ExamRepository examRepository,
            ProctorRepository proctorRepository) {
        this.messagingTemplate = messagingTemplate;
        this.examRepository = examRepository;
        this.proctorRepository = proctorRepository;
    }

    public void pushStatus(
            Long examId,
            Long attemptId,
            String studentId,
            String status,
            OffsetDateTime lastHeartbeatAt,
            OffsetDateTime submittedAt) {

        examRepository.findById(examId)
                .filter(exam -> exam.getProctorId() != null)
                .filter(exam -> proctorRepository.existsById(exam.getProctorId()))
                .ifPresent(exam -> send(
                        exam.getId(), exam.getProctorId(), attemptId, studentId, status, lastHeartbeatAt, submittedAt));
    }

    /**
     * Destination-user key MUST be {@code JwtPrincipal.getName()} (userId as a string) —
     * that's the identity Spring's user-destination resolver keyed off of at STOMP CONNECT
     * time (see StompAuthChannelInterceptor#authenticate), not the proctor's username.
     */
    private void send(
            Long examId,
            Long proctorId,
            Long attemptId,
            String studentId,
            String status,
            OffsetDateTime lastHeartbeatAt,
            OffsetDateTime submittedAt) {

        ProctorStatusMessage message = new ProctorStatusMessage(
                attemptId, examId, studentId, status, lastHeartbeatAt, submittedAt, OffsetDateTime.now());

        try {
            messagingTemplate.convertAndSendToUser(
                    proctorId.toString(), "/" + examId + "/queue/attempt-status", message);
        } catch (Exception exception) {
            // Realtime push is best-effort; never let a WebSocket delivery failure break
            // the underlying student-facing transition that triggered this push.
            LOGGER.warn("Failed to push proctor status for attempt {} to proctor {}", attemptId, proctorId, exception);
        }
    }
}
