package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.StudentEnrollmentConflictEvent;
import com.pte.session.dto.response.SlotConflict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Notifies tenant hosts when bulk enrollment skips students with overlapping exams. */
@Component
public class StudentEnrollmentConflictNotificationListener {

    private final IdentityService identityService;
    private final NotificationDispatchService dispatchService;

    public StudentEnrollmentConflictNotificationListener(IdentityService identityService,
            NotificationDispatchService dispatchService) {
        this.identityService = identityService;
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onStudentEnrollmentConflict(StudentEnrollmentConflictEvent event) {
        if (event == null || event.conflicts() == null || event.conflicts().isEmpty()) {
            return;
        }
        String sessionLabel = event.sessionName() == null || event.sessionName().isBlank()
                ? event.sessionPublicId().toString() : event.sessionName();
        String details = event.conflicts().stream()
                .map(this::formatConflict)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String body = NotificationConstants.STUDENT_ENROLLMENT_CONFLICT_BODY.formatted(sessionLabel, details);
        for (var hostAdmin : identityService.findActiveByTenantIdAndRole(event.tenantId(), Role.HOST_ADMIN)) {
            String dedupeKey = NotificationConstants.STUDENT_ENROLLMENT_CONFLICT_DEDUPE_KEY.formatted(
                    event.sessionPublicId(), hostAdmin.getPublicId(), details.hashCode());
            dispatchService.dispatchTo(NotificationType.STUDENT_ENROLLMENT_CONFLICT, hostAdmin, event.tenantId(),
                    NotificationConstants.STUDENT_ENROLLMENT_CONFLICT_SUBJECT, body, dedupeKey);
        }
    }

    private String formatConflict(SlotConflict conflict) {
        String sessionLabel = conflict.conflictingSessionName() == null
                || conflict.conflictingSessionName().isBlank()
                ? conflict.conflictingSessionPublicId().toString() : conflict.conflictingSessionName();
        return conflict.studentPublicId() + " -> " + sessionLabel;
    }
}
