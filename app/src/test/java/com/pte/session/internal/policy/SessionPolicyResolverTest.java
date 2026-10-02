package com.pte.session.internal.policy;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.internal.exception.InvalidLockdownModeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPolicyResolverTest {

    @Test
    void create_practiceWithNull_defaultsToNone() {
        assertThat(SessionPolicyResolver.resolveForCreate(ExamMode.PRACTICE, null))
                .isEqualTo(LockdownMode.NONE);
    }

    @Test
    void create_practiceWithStandard_preservesStandard() {
        assertThat(SessionPolicyResolver.resolveForCreate(ExamMode.PRACTICE, LockdownMode.STANDARD))
                .isEqualTo(LockdownMode.STANDARD);
    }

    @Test
    void create_practiceWithStrict_isRejected() {
        assertInvalid(() -> SessionPolicyResolver.resolveForCreate(ExamMode.PRACTICE, LockdownMode.STRICT));
    }

    @Test
    void create_officialWithNull_defaultsToStrict() {
        assertThat(SessionPolicyResolver.resolveForCreate(ExamMode.OFFICIAL_EXAM, null))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void create_officialWithStrict_isAllowed() {
        assertThat(SessionPolicyResolver.resolveForCreate(ExamMode.OFFICIAL_EXAM, LockdownMode.STRICT))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void create_officialWithNoneOrStandard_isRejected() {
        assertInvalid(() -> SessionPolicyResolver.resolveForCreate(ExamMode.OFFICIAL_EXAM, LockdownMode.NONE));
        assertInvalid(() -> SessionPolicyResolver.resolveForCreate(ExamMode.OFFICIAL_EXAM, LockdownMode.STANDARD));
    }

    @Test
    void draftPatch_sameModeWithoutLockdown_preservesStandard() {
        assertThat(SessionPolicyResolver.resolveForDraftPatch(
                ExamMode.PRACTICE, LockdownMode.STANDARD, null, null))
                .isEqualTo(LockdownMode.STANDARD);
    }

    @Test
    void draftPatch_modeChangeWithoutLockdown_derivesNewModeDefault() {
        assertThat(SessionPolicyResolver.resolveForDraftPatch(
                ExamMode.PRACTICE, LockdownMode.STANDARD, ExamMode.OFFICIAL_EXAM, null))
                .isEqualTo(LockdownMode.STRICT);
        assertThat(SessionPolicyResolver.resolveForDraftPatch(
                ExamMode.OFFICIAL_EXAM, LockdownMode.STRICT, ExamMode.PRACTICE, null))
                .isEqualTo(LockdownMode.NONE);
    }

    @Test
    void draftPatch_explicitInvalidValue_isRejectedForEffectiveMode() {
        assertInvalid(() -> SessionPolicyResolver.resolveForDraftPatch(
                ExamMode.PRACTICE, LockdownMode.NONE, ExamMode.OFFICIAL_EXAM, LockdownMode.STANDARD));
        assertInvalid(() -> SessionPolicyResolver.resolveForDraftPatch(
                ExamMode.OFFICIAL_EXAM, LockdownMode.STRICT, ExamMode.PRACTICE, LockdownMode.STRICT));
    }

    @Test
    void policyPatch_nullIsNoOp() {
        assertThat(SessionPolicyResolver.resolveForPolicyPatch(
                ExamMode.PRACTICE, LockdownMode.STANDARD, null))
                .isEqualTo(LockdownMode.STANDARD);
    }

    @Test
    void policyPatch_invalidCombinations_areRejected() {
        assertInvalid(() -> SessionPolicyResolver.resolveForPolicyPatch(
                ExamMode.PRACTICE, LockdownMode.NONE, LockdownMode.STRICT));
        assertInvalid(() -> SessionPolicyResolver.resolveForPolicyPatch(
                ExamMode.OFFICIAL_EXAM, LockdownMode.STRICT, LockdownMode.NONE));
        assertInvalid(() -> SessionPolicyResolver.resolveForPolicyPatch(
                ExamMode.OFFICIAL_EXAM, LockdownMode.STRICT, LockdownMode.STANDARD));
    }

    @Test
    void legacyNullPolicy_resolvesByExamMode() {
        assertThat(SessionPolicyResolver.resolveEffectiveLockdownMode(
                ExamMode.PRACTICE, null, true)).isEqualTo(LockdownMode.NONE);
        assertThat(SessionPolicyResolver.resolveEffectiveLockdownMode(
                ExamMode.OFFICIAL_EXAM, null, true)).isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void nonLegacyNullPolicy_failsClosed() {
        assertThatThrownBy(() -> SessionPolicyResolver.resolveEffectiveLockdownMode(
                ExamMode.PRACTICE, null, false))
                .isInstanceOf(InvalidLockdownModeException.class);
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidLockdownModeException.class);
    }
}
