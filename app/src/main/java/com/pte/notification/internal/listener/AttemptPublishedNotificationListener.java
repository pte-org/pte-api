package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails a student their scores once reporting's host-gated publish command
 * marks the report visible. This email is the only way a student receives a
 * score, so it carries the scores and never asks the student to log in.
 */
@Component
public class AttemptPublishedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AttemptPublishedNotificationListener.class);

    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;

    public AttemptPublishedNotificationListener(IdentityService identityService,
            NotificationDispatchService dispatchService) {
        this.identityService = identityService;
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onAttemptPublished(AttemptPublishedEvent event) {
        User student = identityService.findById(event.studentPublicId()).orElse(null);
        if (student == null || student.getEmail() == null || student.getEmail().isBlank()) {
            log.warn(NotificationConstants.ATTEMPT_RESULT_NO_RECIPIENT_EMAIL, event.attemptPublicId(),
                    event.tenantId());
            return;
        }
        // Sensitive variant: the scores are mailed but not retained in the notification history.
        dispatchService.dispatchExternalSensitive(NotificationType.ATTEMPT_PUBLISHED, student.getEmail(),
                event.tenantId(), String.format(NotificationConstants.ATTEMPT_RESULT_DEDUPE_KEY,
                        event.attemptPublicId()),
                NotificationConstants.ATTEMPT_RESULT_SUBJECT, body(student, event));
    }

    private static String body(User student, AttemptPublishedEvent event) {
        String greeting = student.getFullName() == null || student.getFullName().isBlank()
                ? NotificationConstants.ATTEMPT_RESULT_GREETING_ANONYMOUS
                : String.format(NotificationConstants.ATTEMPT_RESULT_GREETING_NAMED, student.getFullName().trim());
        return String.join("\n", greeting, "", NotificationConstants.ATTEMPT_RESULT_INTRO, "",
                line(NotificationConstants.ATTEMPT_RESULT_OVERALL_LABEL, event.overallScore()),
                line(NotificationConstants.ATTEMPT_RESULT_LISTENING_LABEL, event.listeningScore()),
                line(NotificationConstants.ATTEMPT_RESULT_READING_LABEL, event.readingScore()),
                line(NotificationConstants.ATTEMPT_RESULT_SPEAKING_LABEL, event.speakingScore()),
                line(NotificationConstants.ATTEMPT_RESULT_WRITING_LABEL, event.writingScore()),
                "", NotificationConstants.ATTEMPT_RESULT_FOOTER);
    }

    private static String line(String label, Integer score) {
        return String.format(NotificationConstants.ATTEMPT_RESULT_LINE, label,
                score == null ? NotificationConstants.ATTEMPT_RESULT_INSUFFICIENT_DATA : String.valueOf(score));
    }
}
