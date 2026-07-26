package com.pte.notification.service;

import com.pte.notification.constant.NotificationConstants;
import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.UserDirectoryEntry;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.messaging.job.EmailJob;
import com.pte.notification.repository.NotificationLogRepository;
import com.pte.notification.repository.UserDirectoryRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Shared by all 3 event-triggered consumers (enrollment/publish/violation):
 * resolves the recipient's email from the local {@link UserDirectoryEntry}
 * directory, records a {@link NotificationLog} row, and enqueues the actual
 * send (never sends inline — see phase-11 Design Constraints). If the
 * directory has no entry yet (the documented cross-topic ordering gap), the
 * notification is silently skipped rather than failing the whole consumer.
 */
@Service
public class NotificationDispatchService {

    private final UserDirectoryRepository userDirectoryRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final RabbitTemplate rabbitTemplate;

    public NotificationDispatchService(UserDirectoryRepository userDirectoryRepository,
                                       NotificationLogRepository notificationLogRepository, RabbitTemplate rabbitTemplate) {
        this.userDirectoryRepository = userDirectoryRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void dispatch(NotificationType type, UUID recipientUserPublicId, UUID tenantId, String subject, String body) {
        userDirectoryRepository.findByUserPublicId(recipientUserPublicId)
                .ifPresent(entry -> dispatchTo(type, entry, tenantId, subject, body));
    }

    /**
     * Same as {@link #dispatch} but for a caller that already has the {@link
     * UserDirectoryEntry} loaded (e.g. fanning out to N recipients from one
     * query) — avoids one redundant lookup per recipient.
     */
    @Transactional
    public void dispatchTo(NotificationType type, UserDirectoryEntry entry, UUID tenantId, String subject, String body) {
        NotificationLog log = new NotificationLog();
        log.setRecipientUserPublicId(entry.getUserPublicId());
        log.setRecipientEmail(entry.getEmail());
        log.setTenantId(tenantId);
        log.setNotificationType(type);
        log.setSubject(subject);
        log.setBody(body);
        NotificationLog saved = notificationLogRepository.save(log);

        rabbitTemplate.convertAndSend(NotificationConstants.EMAIL_EXCHANGE, NotificationConstants.EMAIL_ROUTING_KEY,
                new EmailJob(saved.getPublicId(), entry.getEmail(), subject, body));
    }
}
