package com.pte.session.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a student is enrolled (single or bulk) commits successfully.
 * {@code notification} (Phase 09) listens for this via {@code
 * @TransactionalEventListener} to send an enrollment email — session has no
 * compile-time dependency on notification; this event type is the entire
 * contract between them. It carries what the email tells the student about the exam (name, opening and closing
 * time). The exam code is left out on purpose: a proctor gives it out on exam day, so it must not travel by email.
 */
public record StudentEnrolledEvent(UUID sessionPublicId, UUID studentPublicId, UUID tenantId, String sessionName,
        Instant opensAt, Instant closesAt) {

    /** For callers that only know who was enrolled where; the email then leaves out the exam details. */
    public StudentEnrolledEvent(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
        this(sessionPublicId, studentPublicId, tenantId, null, null, null);
    }
}
