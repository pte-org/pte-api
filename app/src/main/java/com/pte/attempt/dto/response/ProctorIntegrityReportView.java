package com.pte.attempt.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Integrity report capturing duplicate memberships, tied attempt numbers, and excluded historical orphan attempts.
 */
public record ProctorIntegrityReportView(
        List<UUID> duplicateMembershipStudentPublicIds,
        List<UUID> tiedAttemptNumberStudentPublicIds,
        List<UUID> orphanAttemptPublicIds
) {
    public boolean hasIntegrityViolations() {
        return (duplicateMembershipStudentPublicIds != null && !duplicateMembershipStudentPublicIds.isEmpty())
                || (tiedAttemptNumberStudentPublicIds != null && !tiedAttemptNumberStudentPublicIds.isEmpty());
    }
}
