package com.pte.assessment.internal.service;

import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentAccessPolicyTest {

    private final AssessmentAccessPolicy policy = new AssessmentAccessPolicy();

    @Test
    void academicWorkflowSeparatesDraftOwnershipFromReview() {
        UUID staffId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        CurrentUser staff = new CurrentUser(staffId, null, List.of("ACADEMIC_STAFF"));
        CurrentUser manager = new CurrentUser(managerId, null, List.of("ACADEMIC_MANAGER"));

        assertThat(policy.canAuthor(staff)).isTrue();
        assertThat(policy.canModifyDraft(staff, staffId)).isTrue();
        assertThat(policy.canModifyDraft(staff, managerId)).isFalse();
        assertThat(policy.canApprove(manager, staffId)).isTrue();
        assertThat(policy.canApprove(manager, managerId)).isFalse();
        assertThat(policy.canApprove(manager, null)).isFalse();
    }
}
