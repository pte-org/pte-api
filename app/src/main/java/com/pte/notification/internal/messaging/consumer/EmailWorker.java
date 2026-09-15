package com.pte.notification.internal.messaging.consumer;

import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.messaging.job.EmailJob;
import com.pte.notification.internal.repository.NotificationLogRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Consumes {@link EmailJob} from the RabbitMQ work queue and sends via {@link
 * JavaMailSender} (real SMTP, against Mailpit). Any exception propagates to
 * the container's retry advice ({@code RabbitMqConfig}) — bounded retry with
 * backoff, then dead-lettered; {@link #onDeadLettered} marks the row {@code
 * FAILED} so a host sees it, instead of it silently vanishing into the DLQ.
 */
@Component
public class EmailWorker {

    private final NotificationLogRepository notificationLogRepository;
    private final JavaMailSender mailSender;

    public EmailWorker(NotificationLogRepository notificationLogRepository, JavaMailSender mailSender) {
        this.notificationLogRepository = notificationLogRepository;
        this.mailSender = mailSender;
    }

    @RabbitListener(queues = NotificationConstants.EMAIL_QUEUE, containerFactory = "notificationRabbitListenerContainerFactory")
    @Transactional
    public void onEmailJob(EmailJob job) {
        Optional<NotificationLog> maybeLog = notificationLogRepository.findByPublicId(job.notificationLogPublicId());
        if (maybeLog.isEmpty()) {
            return;
        }
        NotificationLog log = maybeLog.get();
        if (log.getStatus() != NotificationStatus.PENDING) {
            return; // Already terminal — redelivery no-op.
        }

        sendEmail(job);
        log.markSent();
        notificationLogRepository.save(log);
    }

    @RabbitListener(queues = NotificationConstants.EMAIL_DLQ, containerFactory = "notificationRabbitListenerContainerFactory")
    @Transactional
    public void onDeadLettered(EmailJob job) {
        notificationLogRepository.findByPublicId(job.notificationLogPublicId()).ifPresent(log -> {
            if (log.getStatus() == NotificationStatus.PENDING) {
                log.markFailed();
                notificationLogRepository.save(log);
            }
        });
    }

    private void sendEmail(EmailJob job) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(job.recipientEmail());
        message.setSubject(job.subject());
        message.setText(job.body());
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new IllegalStateException(
                    String.format(NotificationConstants.EMAIL_SEND_FAILED, job.notificationLogPublicId()), ex);
        }
    }
}
