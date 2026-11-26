package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.notification.internal.template.ExamEnrollmentEmailTemplate;
import com.pte.notification.internal.template.RenderedEmail;
import com.pte.session.dto.event.StudentEnrolledEvent;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;

/**
 * Notifies a student they've been enrolled in a session. {@code
 * @TransactionalEventListener} (default phase AFTER_COMMIT): fires only after
 * {@code EnrollmentService}'s transaction actually commits, so a rolled-back
 * enrollment never sends an email. The mail carries the exam's schedule itself, because students have no web page
 * to look it up on.
 */
@Component
public class EnrollmentNotificationListener {

    private final IdentityService identityService;
    private final TenancyService tenancyService;
    private final NotificationDispatchService dispatchService;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    public EnrollmentNotificationListener(IdentityService identityService, TenancyService tenancyService,
            NotificationDispatchService dispatchService, NotificationProperties notificationProperties,
            Clock clock) {
        this.identityService = identityService;
        this.tenancyService = tenancyService;
        this.dispatchService = dispatchService;
        this.notificationProperties = notificationProperties;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onStudentEnrolled(StudentEnrolledEvent event) {
        User student = identityService.findById(event.studentPublicId()).orElse(null);
        String organizationName = event.tenantId() == null ? null
                : tenancyService.findOrganizationName(event.tenantId()).orElse(null);
        RenderedEmail email = ExamEnrollmentEmailTemplate.render(student == null ? null : student.getFullName(),
                event.sessionName(), organizationName, event.opensAt(), event.closesAt(),
                ZoneId.of(notificationProperties.getDisplayZone()), Year.now(clock).getValue());
        dispatchService.dispatch(NotificationType.STUDENT_ENROLLED, student, event.tenantId(), email.subject(),
                email.text(), null, email.html());
    }
}
