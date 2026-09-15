package com.pte.notification.internal.service;

import com.pte.identity.domain.User;
import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.messaging.job.EmailJob;
import com.pte.notification.internal.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Shared by every event listener (enrollment/report-publish/violation):
 * resolves the recipient's email via {@code identity} (the live table, not a
 * lagging local copy — no ordering gap to wait out anymore), records a
 * {@link NotificationLog} row, and enqueues the actual send (never sends
 * inline). A recipient publicId with no matching {@code identity} user is
 * logged and skipped rather than failing the whole listener — an explicit
 * policy, not silence: unlike the old cross-topic directory-lag case, this
 * now only happens for a genuinely bad/stale publicId.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationLogRepository notificationLogRepository;
    private final RabbitTemplate rabbitTemplate;

    public NotificationDispatchService(NotificationLogRepository notificationLogRepository, RabbitTemplate rabbitTemplate) {
        this.notificationLogRepository = notificationLogRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void dispatch(NotificationType type, User recipient, UUID tenantId, String subject, String body) {
        if (recipient == null) {
            log.warn("Skipping {} notification (tenantId={}): recipient not found in identity", type, tenantId);
            return;
        }
        dispatchTo(type, recipient, tenantId, subject, body);
    }

    /** Same as {@link #dispatch} but for a caller fanning out to N already-loaded recipients — avoids a redundant lookup per recipient. */
    @Transactional
    public void dispatchTo(NotificationType type, User recipient, UUID tenantId, String subject, String body) {
        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setRecipientUserPublicId(recipient.getPublicId());
        notificationLog.setRecipientEmail(recipient.getEmail());
        notificationLog.setTenantId(tenantId);
        notificationLog.setNotificationType(type);
        notificationLog.setSubject(subject);
        notificationLog.setBody(body);
        NotificationLog saved = notificationLogRepository.save(notificationLog);

        rabbitTemplate.convertAndSend(NotificationConstants.EMAIL_EXCHANGE, NotificationConstants.EMAIL_ROUTING_KEY,
                new EmailJob(saved.getPublicId(), recipient.getEmail(), subject, body));
    }
}
