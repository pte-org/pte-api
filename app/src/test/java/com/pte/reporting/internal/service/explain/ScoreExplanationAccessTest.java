package com.pte.reporting.internal.service.explain;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.exception.ReportNotFoundException;
import com.pte.scoring.ScoringService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tenant isolation and examiner assignment (TEST_SCENARIOS_IAM authz-guard pattern): Host of the attempt's tenant and
 * the assigned Examiner read it; everyone else gets the same 404 REPORT_NOT_FOUND, so existence never leaks.
 */
class ScoreExplanationAccessTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID ATTEMPT = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final AttemptSummaryView ATTEMPT_IN_A = new AttemptSummaryView(ATTEMPT, SESSION, UUID.randomUUID(),
            TENANT_A, UUID.randomUUID());

    private final AttemptService attempts = mock(AttemptService.class);
    private final ScoringService scoring = mock(ScoringService.class);
    private final ScoreExplanationAccess access = new ScoreExplanationAccess(attempts, scoring);

    @Test
    void aHostOfTheAttemptsTenantReadsIt() {
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenReturn(ATTEMPT_IN_A);

        assertThat(access.requireReadable(ATTEMPT, user(TENANT_A, "HOST_ADMIN"))).isEqualTo(ATTEMPT_IN_A);
        verifyNoInteractions(scoring);
    }

    @Test
    void aHostOfAnotherTenantGetsNotFound() {
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenReturn(ATTEMPT_IN_A);

        assertHidden(() -> access.requireReadable(ATTEMPT, user(TENANT_B, "HOST_ADMIN")));
    }

    @Test
    void anAssignedExaminerOfTheTenantReadsIt() {
        CurrentUser examiner = user(TENANT_A, "EXAMINER");
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenReturn(ATTEMPT_IN_A);
        when(scoring.isAttemptAssignedToExaminer(TENANT_A, SESSION, ATTEMPT, examiner.userId())).thenReturn(true);

        assertThat(access.requireReadable(ATTEMPT, examiner)).isEqualTo(ATTEMPT_IN_A);
        verify(scoring).isAttemptAssignedToExaminer(TENANT_A, SESSION, ATTEMPT, examiner.userId());
    }

    @Test
    void anExaminerNotAssignedToTheAttemptGetsNotFound() {
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenReturn(ATTEMPT_IN_A);
        when(scoring.isAttemptAssignedToExaminer(any(), any(), any(), any())).thenReturn(false);

        assertHidden(() -> access.requireReadable(ATTEMPT, user(TENANT_A, "EXAMINER")));
    }

    @Test
    void anExaminerOfAnotherTenantGetsNotFoundBeforeAnyAssignmentLookup() {
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenReturn(ATTEMPT_IN_A);

        assertHidden(() -> access.requireReadable(ATTEMPT, user(TENANT_B, "EXAMINER")));
        verifyNoInteractions(scoring);
    }

    @Test
    void aMissingOrUnsubmittedAttemptLooksExactlyLikeAHiddenOne() {
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenThrow(new DomainException(HttpStatus.NOT_FOUND,
                "ATTEMPT_NOT_FOUND") { });

        assertHidden(() -> access.requireReadable(ATTEMPT, user(TENANT_A, "HOST_ADMIN")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR", "PROCTOR",
            "STUDENT"})
    void anyOtherRoleIsRefusedEvenInsideTheTenant(String role) {
        when(attempts.getSubmittedAttempt(ATTEMPT)).thenReturn(ATTEMPT_IN_A);

        assertHidden(() -> access.requireReadable(ATTEMPT, user(TENANT_A, role)));
        verifyNoInteractions(attempts, scoring);
    }

    @Test
    void aPlatformUserWithoutTenantIsRefused() {
        assertHidden(() -> access.requireReadable(ATTEMPT, user(null, "HOST_ADMIN")));
        assertHidden(() -> access.requireReadable(ATTEMPT, null));
        verifyNoInteractions(attempts, scoring);
    }

    private static CurrentUser user(UUID tenantId, String role) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of(role));
    }

    private static void assertHidden(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ReportNotFoundException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(error.getCode()).isEqualTo(ReportingConstants.REPORT_NOT_FOUND);
        });
    }
}
