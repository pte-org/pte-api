package com.aptis.modules.examdelivery.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.domain.ExamAttemptStatus;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.proctor.domain.ProctorActionType;
import com.aptis.modules.proctor.dto.BroadcastAnnouncement;
import com.aptis.modules.proctor.service.ProctorActionAuditService;

/**
 * The one-to-many privileged proctor action: unlike force-submit/extend-time/flag, this
 * mutates no ExamAttempt row, so no pessimistic lock is needed (Design Constraint) — only
 * the exam-level proctor assignment is checked before sending.
 */
@Service
public class ProctorBroadcastService {

    private final ExamRepository examRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProctorActionAuditService auditService;

    public ProctorBroadcastService(
            ExamRepository examRepository,
            ExamAttemptRepository examAttemptRepository,
            SimpMessagingTemplate messagingTemplate,
            ProctorActionAuditService auditService) {
        this.examRepository = examRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.messagingTemplate = messagingTemplate;
        this.auditService = auditService;
    }

    @Transactional
    public int broadcast(Long examId, String message, JwtPrincipal proctorPrincipal) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        if (exam.getProctorId() == null || !exam.getProctorId().equals(proctorPrincipal.userId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Not assigned to this exam session");
        }

        long recipientCount = examAttemptRepository.countByExamIdAndStatusIn(
                examId, List.of(ExamAttemptStatus.IN_PROGRESS, ExamAttemptStatus.SECTION_SWITCH));

        // Plaintext only — no HTML/Markdown parsing (Design Constraint); client escapes on display.
        messagingTemplate.convertAndSend(
                "/topic/exam/" + examId + "/announcements", new BroadcastAnnouncement(examId, message));

        Map<String, Object> details = new HashMap<>();
        details.put("messageText", message);
        details.put("recipientCount", recipientCount);
        auditService.record(proctorPrincipal.userId(), null, ProctorActionType.BROADCAST_ANNOUNCEMENT, details);

        return (int) recipientCount;
    }
}
