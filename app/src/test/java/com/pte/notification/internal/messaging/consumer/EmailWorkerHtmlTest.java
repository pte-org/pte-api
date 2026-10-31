package com.pte.notification.internal.messaging.consumer;

import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.messaging.job.EmailJob;
import com.pte.notification.internal.repository.NotificationLogRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A job that carries HTML goes out as one multipart message: the card and its plain-text twin. */
@ExtendWith(MockitoExtension.class)
class EmailWorkerHtmlTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private JavaMailSender mailSender;

    private EmailWorker worker;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.setMailFrom("no-reply@pteprep.test");
        worker = new EmailWorker(notificationLogRepository, mailSender, properties, com.pte.TestClocks.fixed());
    }

    private NotificationLog pendingLog(UUID id) {
        NotificationLog log = new NotificationLog();
        log.setPublicId(id);
        log.setStatus(NotificationStatus.PENDING);
        return log;
    }

    /** Collects the text parts of a (possibly nested) multipart message by MIME type. */
    private static void collect(Part part, Map<String, String> found) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collect(child, found);
            }
        } else if (part.isMimeType("text/plain")) {
            found.put("text/plain", (String) part.getContent());
        } else if (part.isMimeType("text/html")) {
            found.put("text/html", (String) part.getContent());
        } else if (part.isMimeType("image/png")) {
            found.put("image/png", part.getFileName() == null ? "inline" : part.getFileName());
        }
    }

    @Test
    void htmlJob_isSentAsMultipartWithBothTheCardAndItsPlainTextTwin() throws Exception {
        UUID id = UUID.randomUUID();
        MimeMessage mime = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mime);
        when(notificationLogRepository.findByPublicId(id)).thenReturn(Optional.of(pendingLog(id)));

        worker.onEmailJob(new EmailJob(id, "host@example.com", "Đặt lại mật khẩu / Reset", "plain twin",
                "<p>Đặt lại mật khẩu</p>"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        MimeMessage message = sent.getValue();
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@pteprep.test");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("host@example.com");
        assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu / Reset");
        message.saveChanges(); // fills in the content types of the nested parts
        Map<String, String> parts = new HashMap<>();
        collect(message, parts);
        assertThat(parts).containsEntry("text/plain", "plain twin").containsEntry("text/html", "<p>Đặt lại mật khẩu</p>");
    }

    @Test
    void htmlJob_attachesTheProjectLogoWhenTheCardReferencesIt() throws Exception {
        UUID id = UUID.randomUUID();
        MimeMessage mime = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mime);
        when(notificationLogRepository.findByPublicId(id)).thenReturn(Optional.of(pendingLog(id)));

        worker.onEmailJob(new EmailJob(id, "host@example.com", "s", "t", "<img src=\"cid:pte-logo\">"));

        mime.saveChanges();
        Map<String, String> parts = new HashMap<>();
        collect(mime, parts);
        assertThat(parts).containsKey("image/png");
    }

    @Test
    void htmlJob_marksTheLogSent() {
        UUID id = UUID.randomUUID();
        NotificationLog log = pendingLog(id);
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        when(notificationLogRepository.findByPublicId(id)).thenReturn(Optional.of(log));

        worker.onEmailJob(new EmailJob(id, "host@example.com", "s", "t", "<p>h</p>"));

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void blankHtml_fallsBackToPlainText() {
        UUID id = UUID.randomUUID();
        when(notificationLogRepository.findByPublicId(id)).thenReturn(Optional.of(pendingLog(id)));

        worker.onEmailJob(new EmailJob(id, "host@example.com", "s", "t", "   "));

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }
}
