package com.pte.notification.internal.messaging.consumer;

import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.messaging.job.EmailJob;
import com.pte.notification.internal.repository.NotificationLogRepository;
import com.pte.notification.internal.template.BrandedEmailTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

    /** The project's logo mark, attached inline to HTML cards (see {@link BrandedEmailTemplate#LOGO_CID}). */
    private static final String LOGO_RESOURCE = "notification/logo-mark.png";

    private final NotificationLogRepository notificationLogRepository;
    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    public EmailWorker(NotificationLogRepository notificationLogRepository, JavaMailSender mailSender,
            NotificationProperties notificationProperties,
            Clock clock) {
        this.notificationLogRepository = notificationLogRepository;
        this.mailSender = mailSender;
        this.notificationProperties = notificationProperties;
        this.clock = clock;
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
        log.markSent(clock.instant());
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
        if (job.htmlBody() != null && !job.htmlBody().isBlank()) {
            sendHtmlEmail(job);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(notificationProperties.getMailFrom());
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

    /** One multipart/alternative message: clients that cannot show the HTML card show its plain-text twin. */
    private void sendHtmlEmail(EmailJob job) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(notificationProperties.getMailFrom());
            helper.setTo(job.recipientEmail());
            helper.setSubject(job.subject());
            helper.setText(job.body(), job.htmlBody());
            if (job.htmlBody().contains("cid:" + BrandedEmailTemplate.LOGO_CID)) {
                helper.addInline(BrandedEmailTemplate.LOGO_CID, new ClassPathResource(LOGO_RESOURCE), "image/png");
            }
            mailSender.send(message);
        } catch (MailException | MessagingException ex) {
            throw new IllegalStateException(
                    String.format(NotificationConstants.EMAIL_SEND_FAILED, job.notificationLogPublicId()), ex);
        }
    }
}
