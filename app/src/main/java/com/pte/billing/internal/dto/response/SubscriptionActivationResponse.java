package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Common result shape for both subscription and student-capacity activation.
 * Fields not applicable to {@link ActivationKind#STUDENT_CAPACITY} are null by
 * contract; callers branch on {@code kind}, not on the selected Plan type.
 */
public record SubscriptionActivationResponse(
        ActivationKind kind,
        UUID tenantId,
        UUID planId,
        String licenseKey,
        Instant startsAt,
        Instant expiresAt,
        Integer maxStudentsPerSession,
        Integer grantedStudentSlots,
        String status,
        String activationSource) {

    public enum ActivationKind {
        SUBSCRIPTION,
        STUDENT_CAPACITY
    }

    public static SubscriptionActivationResponse fromSubscription(UUID tenantId, UUID planId, String licenseKey,
            Instant startsAt, Instant expiresAt, Integer maxStudentsPerSession, String status,
            String activationSource) {
        return new SubscriptionActivationResponse(
                ActivationKind.SUBSCRIPTION,
                tenantId,
                planId,
                licenseKey,
                startsAt,
                expiresAt,
                maxStudentsPerSession,
                null,
                status,
                activationSource);
    }

    public static SubscriptionActivationResponse forStudentCapacity(UUID tenantId, UUID planId, int slots) {
        return new SubscriptionActivationResponse(
                ActivationKind.STUDENT_CAPACITY,
                tenantId,
                planId,
                null,
                null,
                null,
                null,
                slots,
                null,
                null);
    }
}
