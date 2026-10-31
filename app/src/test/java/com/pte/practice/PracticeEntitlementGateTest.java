package com.pte.practice;

import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The chat module reaches practice only through this public, root-package gate. */
class PracticeEntitlementGateTest {

    @Test
    void gate_lives_in_the_practice_root_package_and_is_an_interface() {
        assertThat(PracticeEntitlementGate.class.getPackageName()).isEqualTo("com.pte.practice");
        assertThat(PracticeEntitlementGate.class.isInterface()).isTrue();
    }

    @Test
    void entitlement_service_implements_the_gate() {
        assertThat(PracticeEntitlementGate.class.isAssignableFrom(PracticeEntitlementService.class)).isTrue();
    }

    @Test
    void gate_declares_assert_unlocked_with_caller_and_organization() throws NoSuchMethodException {
        Method method = PracticeEntitlementGate.class
                .getMethod("assertUnlocked", CurrentUser.class, UUID.class);

        assertThat(method.getReturnType()).isEqualTo(void.class);
    }
}
