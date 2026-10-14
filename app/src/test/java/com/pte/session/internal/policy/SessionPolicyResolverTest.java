package com.pte.session.internal.policy;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.internal.exception.InvalidLockdownModeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPolicyResolverTest {

    @Test
    void create_withNull_defaultsToStrict() {
        assertThat(SessionPolicyResolver.resolveForCreate(null))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void create_withStrict_isAllowed() {
        assertThat(SessionPolicyResolver.resolveForCreate(LockdownMode.STRICT))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void create_withNoneOrStandard_isRejected() {
        assertInvalid(() -> SessionPolicyResolver.resolveForCreate(LockdownMode.NONE));
        assertInvalid(() -> SessionPolicyResolver.resolveForCreate(LockdownMode.STANDARD));
    }

    @Test
    void draftPatch_withoutLockdown_preservesCurrentStrict() {
        assertThat(SessionPolicyResolver.resolveForDraftPatch(
                false, LockdownMode.STRICT, null))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void draftPatch_legacyNullLockdown_defaultsToStrict() {
        assertThat(SessionPolicyResolver.resolveForDraftPatch(true, null, null))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void draftPatch_explicitNonStrictValue_isRejected() {
        assertInvalid(() -> SessionPolicyResolver.resolveForDraftPatch(
                false, LockdownMode.STRICT, LockdownMode.STANDARD));
        assertInvalid(() -> SessionPolicyResolver.resolveForDraftPatch(
                false, LockdownMode.STRICT, LockdownMode.NONE));
    }

    @Test
    void policyPatch_nullIsNoOp() {
        assertThat(SessionPolicyResolver.resolveForPolicyPatch(LockdownMode.STRICT, null))
                .isEqualTo(LockdownMode.STRICT);
    }

    @Test
    void policyPatch_nonStrictValues_areRejected() {
        assertInvalid(() -> SessionPolicyResolver.resolveForPolicyPatch(LockdownMode.STRICT, LockdownMode.NONE));
        assertInvalid(() -> SessionPolicyResolver.resolveForPolicyPatch(LockdownMode.STRICT, LockdownMode.STANDARD));
    }

    @Test
    void legacyNullPolicy_resolvesByExamMode() {
        assertThat(SessionPolicyResolver.resolveEffectiveLockdownMode(
                ExamMode.OFFICIAL_EXAM, null, true)).isEqualTo(LockdownMode.STRICT);
        assertThat(SessionPolicyResolver.resolveEffectiveLockdownMode(
                null, null, true)).isEqualTo(LockdownMode.STANDARD);
    }

    @Test
    void nonLegacyNullPolicy_failsClosed() {
        assertThatThrownBy(() -> SessionPolicyResolver.resolveEffectiveLockdownMode(
                ExamMode.OFFICIAL_EXAM, null, false))
                .isInstanceOf(InvalidLockdownModeException.class);
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidLockdownModeException.class);
    }
}
