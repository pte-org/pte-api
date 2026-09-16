package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.StudentEnrolledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifies a student they've been enrolled in a session. {@code
 * @TransactionalEventListener} (default phase AFTER_COMMIT): fires only after
 * {@code EnrollmentService}'s transaction actually commits, so a rolled-back
 * enrollment never sends an email.
 */
@Component
public class EnrollmentNotificationListener {

    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;

    public EnrollmentNotificationListener(IdentityService identityService,
            NotificationDispatchService dispatchService) {
        this.identityService = identityService;
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onStudentEnrolled(StudentEnrolledEvent event) {
        dispatchService.dispatch(NotificationType.STUDENT_ENROLLED,
                identityService.findById(event.studentPublicId()).orElse(null),
                event.tenantId(), "You've been enrolled in an exam session",
                "You've been enrolled in exam session " + event.sessionPublicId() + ". Log in to view your schedule.");
    }
}
