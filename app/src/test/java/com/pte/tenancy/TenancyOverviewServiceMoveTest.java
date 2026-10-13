package com.pte.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 prep refactor guard: the five platform tenancy reads of Phase 3 moved verbatim from {@link TenancyService}
 * to the root facade {@link TenancyOverviewService}. The old class must not keep (or regain) them, and the new
 * facade must keep exactly the same public contract (names, parameters, return types, read-only transactions).
 */
class TenancyOverviewServiceMoveTest {

    private static final List<String> MOVED = List.of("platformTenantCounts", "countTenantsCreatedBetween",
            "countTenantsCreatedPerRange", "recentTenants", "seatLimits");

    private static List<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName).sorted().toList();
    }

    @Test
    void theFiveMovedMethodsLiveOnlyOnTheOverviewFacade() {
        assertThat(publicMethodNames(TenancyOverviewService.class)).containsExactlyInAnyOrderElementsOf(MOVED);
        assertThat(publicMethodNames(TenancyService.class)).doesNotContainAnyElementsOf(MOVED);
    }

    @Test
    void theFacadeKeepsTheOriginalSignaturesAndReturnTypes() throws Exception {
        assertThat(TenancyOverviewService.class.getMethod("platformTenantCounts").getReturnType())
                .isEqualTo(PlatformTenantCounts.class);
        assertThat(TenancyOverviewService.class.getMethod("countTenantsCreatedBetween", Instant.class, Instant.class)
                .getReturnType()).isEqualTo(long.class);
        assertThat(TenancyOverviewService.class.getMethod("countTenantsCreatedPerRange", List.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(TenancyOverviewService.class.getMethod("recentTenants", int.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(TenancyOverviewService.class.getMethod("seatLimits").getReturnType()).isEqualTo(List.class);
    }

    @Test
    void theFacadeIsASpringServiceAndEveryReadIsReadOnlyTransactional() {
        assertThat(TenancyOverviewService.class.getAnnotation(Service.class)).isNotNull();
        for (String name : MOVED) {
            Method method = Arrays.stream(TenancyOverviewService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).as(name).isNotNull();
            assertThat(tx.readOnly()).as(name).isTrue();
        }
    }
}
